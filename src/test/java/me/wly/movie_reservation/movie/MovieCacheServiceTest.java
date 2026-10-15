package me.wly.movie_reservation.movie;

import me.wly.movie_reservation.common.redis.RedisCircuitBreaker;
import me.wly.movie_reservation.movie.vo.MovieDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static me.wly.movie_reservation.support.RedisCircuitBreakerTestSupport.elapseCooldown;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieCacheServiceTest {
    private static final String IMDB_ID = "tt0111161";
    private static final String CACHE_KEY = "movie:v1:detail:" + IMDB_ID;
    private static final String LOCK_KEY = "lock:movie:v1:detail:" + IMDB_ID;
    private static final String JSON = "{\"title\":\"The Shawshank Redemption\"}";
    private static final MovieDetailVO MOVIE = new MovieDetailVO(
            "The Shawshank Redemption", LocalDateTime.of(1994, 9, 23, 0, 0),
            "A prison drama", List.of("Drama"), "en", "https://example.com/poster.jpg");

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private ObjectMapper mapper;
    @Mock private Supplier<MovieDetailVO> databaseLoader;
    private RedisCircuitBreaker breaker;
    private MovieCacheService cache;

    @BeforeEach
    void setUp() {
        breaker = new RedisCircuitBreaker();
        cache = new MovieCacheService(breaker, redis, mapper);
        // 熔断时会跳过整个 Redis API，因此此公共桩允许不被使用。
        lenient().when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void rebuild_readsTwiceWritesJitteredCacheAndReleasesOwnedLock() {
        acquireLock();
        loadMovie();

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values, times(2)).get(CACHE_KEY);
        var ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq(CACHE_KEY), eq(JSON), ttl.capture());
        assertTrue(ttl.getValue().compareTo(Duration.ofMinutes(30)) >= 0);
        assertTrue(ttl.getValue().compareTo(Duration.ofMinutes(35)) <= 0);
        var token = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq(LOCK_KEY), token.capture(), eq(Duration.ofSeconds(10)));
        verify(redis).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), eq(token.getValue()));

        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void nullDatabaseResult_writesShortLivedNullPlaceholder() {
        acquireLock();

        assertNull(cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(databaseLoader).get();
        verify(values).set(CACHE_KEY, "__NULL__", Duration.ofSeconds(60));
        verifyNoInteractions(mapper);
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void readFailure_opensCircuitAndLaterRequestSkipsAllRedisOperations() {
        when(values.get(CACHE_KEY)).thenThrow(redisFailure());
        when(databaseLoader.get()).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));
        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values).get(CACHE_KEY);
        verify(databaseLoader, times(2)).get();
        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(mapper);
        assertNull(breaker.tryAcquire());
    }

    @Test
    void openCircuit_skipsRedisEntirely() {
        breaker.onFailure(breaker.tryAcquire());
        when(databaseLoader.get()).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verifyNoInteractions(redis, values, mapper);
        verify(databaseLoader).get();
    }

    @Test
    void lockFailure_loadsDatabaseWithoutWritingCache() {
        when(values.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class)))
                .thenThrow(redisFailure());
        when(databaseLoader.get()).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values).get(CACHE_KEY);
        verifyNoCacheWriteOrUnlock();
        assertNull(breaker.tryAcquire());
    }

    @Test
    void secondReadFailure_skipsCacheWriteAndUnlockWhileCircuitIsOpen() {
        acquireLock();
        when(values.get(CACHE_KEY)).thenReturn(null).thenThrow(redisFailure());
        when(databaseLoader.get()).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values, times(2)).get(CACHE_KEY);
        verify(databaseLoader).get();
        verifyNoCacheWriteOrUnlock();
        assertNull(breaker.tryAcquire());
    }

    @Test
    void retryReadFailure_stopsRetryingAndDoesNotWriteCache() {
        when(values.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class))).thenReturn(false);
        when(values.get(CACHE_KEY)).thenReturn(null).thenThrow(redisFailure());
        when(databaseLoader.get()).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values, times(2)).get(CACHE_KEY);
        verify(databaseLoader).get();
        verifyNoCacheWriteOrUnlock();
        assertNull(breaker.tryAcquire());
    }

    @Test
    void busyLock_readsOtherRequestsRebuiltCacheWithoutDatabaseQuery() {
        when(values.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class))).thenReturn(false);
        when(values.get(CACHE_KEY)).thenReturn(null, JSON);
        when(mapper.readValue(JSON, MovieDetailVO.class)).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verifyNoInteractions(databaseLoader);
        verifyNoCacheWriteOrUnlock();
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void secondReadHit_avoidsDatabaseQueryAndReleasesOwnedLock() {
        acquireLock();
        when(values.get(CACHE_KEY)).thenReturn(null, JSON);
        when(mapper.readValue(JSON, MovieDetailVO.class)).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verifyNoInteractions(databaseLoader);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verify(redis).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), anyString());
    }

    @Test
    void writeFailure_returnsDatabaseResultAndOpensCircuit() {
        acquireLock();
        loadMovie();
        doThrow(redisFailure()).when(values).set(eq(CACHE_KEY), eq(JSON), any(Duration.class));

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString());
        assertNull(breaker.tryAcquire());
    }

    @Test
    void nullCacheWriteFailure_keepsNotFoundResultAndOpensCircuit() {
        acquireLock();
        doThrow(redisFailure()).when(values).set(CACHE_KEY, "__NULL__", Duration.ofSeconds(60));

        assertNull(cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(databaseLoader).get();
        assertNull(breaker.tryAcquire());
    }

    @Test
    void unlockFailure_keepsSuccessfulResultAndOpensCircuit() {
        acquireLock();
        loadMovie();
        when(redis.execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), anyString()))
                .thenThrow(redisFailure());

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values).set(eq(CACHE_KEY), eq(JSON), any(Duration.class));
        assertNull(breaker.tryAcquire());
    }

    @Test
    void corruptCache_isDeletedAndRebuiltWithoutTrippingCircuit() {
        acquireLock();
        loadMovie();
        when(values.get(CACHE_KEY)).thenReturn("invalid-json").thenReturn(null);
        JacksonException failure = mock(JacksonException.class);
        when(mapper.readValue("invalid-json", MovieDetailVO.class)).thenThrow(failure);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(redis).delete(CACHE_KEY);
        verify(values).set(eq(CACHE_KEY), eq(JSON), any(Duration.class));
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void deleteFailure_opensCircuitAndPreventsFurtherRebuildOperations() {
        when(values.get(CACHE_KEY)).thenReturn("invalid-json");
        JacksonException failure = mock(JacksonException.class);
        when(mapper.readValue("invalid-json", MovieDetailVO.class)).thenThrow(failure);
        when(redis.delete(CACHE_KEY)).thenThrow(redisFailure());
        when(databaseLoader.get()).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        assertNull(breaker.tryAcquire());
    }

    @Test
    void serializationFailure_skipsWriteAndDoesNotTripCircuit() {
        acquireLock();
        when(databaseLoader.get()).thenAnswer(invocation -> {
            openCircuitWithElapsedCooldown();
            return MOVIE;
        });
        JacksonException failure = mock(JacksonException.class);
        when(mapper.writeValueAsString(MOVIE)).thenThrow(failure);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verify(redis).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), anyString());
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void halfOpenLockSuccess_allowsSecondReadWriteAndUnlock() {
        acquireLock();
        loadMovie();
        when(values.get(CACHE_KEY)).thenAnswer(invocation -> {
            openCircuitWithElapsedCooldown();
            return null;
        }).thenReturn(null);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values, times(2)).get(CACHE_KEY);
        verify(values).set(eq(CACHE_KEY), eq(JSON), any(Duration.class));
        verify(redis).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), anyString());
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void halfOpenBusyLock_recoversCircuitAndAllowsRetryRead() {
        when(values.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class))).thenReturn(false);
        when(values.get(CACHE_KEY)).thenAnswer(invocation -> {
            openCircuitWithElapsedCooldown();
            return null;
        }).thenReturn(JSON);
        when(mapper.readValue(JSON, MovieDetailVO.class)).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verifyNoInteractions(databaseLoader);
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void halfOpenWriteSuccess_recoversCircuitAndAllowsUnlock() {
        acquireLock();
        when(databaseLoader.get()).thenAnswer(invocation -> {
            openCircuitWithElapsedCooldown();
            return MOVIE;
        });
        when(mapper.writeValueAsString(MOVIE)).thenReturn(JSON);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values).set(eq(CACHE_KEY), eq(JSON), any(Duration.class));
        verify(redis).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), anyString());
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void halfOpenNullWriteSuccess_recoversCircuitAndAllowsUnlock() {
        acquireLock();
        when(databaseLoader.get()).thenAnswer(invocation -> {
            openCircuitWithElapsedCooldown();
            return null;
        });

        assertNull(cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values).set(CACHE_KEY, "__NULL__", Duration.ofSeconds(60));
        verify(redis).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), anyString());
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void halfOpenUnlockSuccess_recoversCircuit() {
        acquireLock();
        loadMovie();
        doAnswer(invocation -> {
            openCircuitWithElapsedCooldown();
            return null;
        }).when(values).set(eq(CACHE_KEY), eq(JSON), any(Duration.class));

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(redis).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), anyString());
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void halfOpenDeleteSuccess_recoversCircuitAndAllowsRebuild() {
        acquireLock();
        loadMovie();
        when(values.get(CACHE_KEY)).thenReturn("invalid-json").thenReturn(null);
        JacksonException failure = mock(JacksonException.class);
        when(mapper.readValue("invalid-json", MovieDetailVO.class)).thenAnswer(invocation -> {
            openCircuitWithElapsedCooldown();
            throw failure;
        });

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(redis).delete(CACHE_KEY);
        verify(values).set(eq(CACHE_KEY), eq(JSON), any(Duration.class));
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void databaseFailure_propagatesAndReleasesLockWithoutTrippingCircuit() {
        acquireLock();
        var failure = new DataAccessResourceFailureException("Database unavailable");
        when(databaseLoader.get()).thenThrow(failure);

        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> cache.getOrLoadDetail(IMDB_ID, databaseLoader)));

        verify(redis).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), anyString());
        verifyNoInteractions(mapper);
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void halfOpenReadMiss_recoversCircuitAndAllowsRebuildAndNextCacheHit() {
        openCircuitWithElapsedCooldown();
        acquireLock();
        loadMovie();
        when(values.get(CACHE_KEY)).thenReturn(null, null, JSON);
        when(mapper.readValue(JSON, MovieDetailVO.class)).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));
        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(databaseLoader).get();
        verify(values, times(3)).get(CACHE_KEY);
        verify(values).set(eq(CACHE_KEY), eq(JSON), any(Duration.class));
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void halfOpenNullCacheHit_recoversCircuitWithoutDatabaseQuery() {
        openCircuitWithElapsedCooldown();
        when(values.get(CACHE_KEY)).thenReturn("__NULL__");

        assertNull(cache.getOrLoadDetail(IMDB_ID, databaseLoader));
        assertNull(cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values, times(2)).get(CACHE_KEY);
        verifyNoInteractions(databaseLoader);
        assertNotNull(breaker.tryAcquire());
    }

    @Test
    void halfOpenReadFailure_reopensCircuitAndLaterRequestSkipsRedis() {
        openCircuitWithElapsedCooldown();
        when(values.get(CACHE_KEY)).thenThrow(redisFailure());
        when(databaseLoader.get()).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));
        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values).get(CACHE_KEY);
        verify(databaseLoader, times(2)).get();
        assertNull(breaker.tryAcquire());
    }

    @Test
    void circuitOpenedAfterInitialRead_preventsLockCommand() {
        when(values.get(CACHE_KEY)).thenAnswer(invocation -> {
            // 模拟另一个并发请求刚发现 Redis 故障。
            breaker.onFailure(breaker.tryAcquire());
            return null;
        });
        when(databaseLoader.get()).thenReturn(MOVIE);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verifyNoCacheWriteOrUnlock();
    }

    @Test
    void circuitOpenedDuringDatabaseLoad_preventsWriteAndUnlockCommands() {
        acquireLock();
        when(databaseLoader.get()).thenAnswer(invocation -> {
            breaker.onFailure(breaker.tryAcquire());
            return MOVIE;
        });
        when(mapper.writeValueAsString(MOVIE)).thenReturn(JSON);

        assertSame(MOVIE, cache.getOrLoadDetail(IMDB_ID, databaseLoader));

        verify(values, times(2)).get(CACHE_KEY);
        verifyNoCacheWriteOrUnlock();
    }

    private void acquireLock() {
        when(values.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(10)))).thenReturn(true);
    }

    private void loadMovie() {
        when(databaseLoader.get()).thenReturn(MOVIE);
        when(mapper.writeValueAsString(MOVIE)).thenReturn(JSON);
    }

    private void verifyNoCacheWriteOrUnlock() {
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    private void openCircuitWithElapsedCooldown() {
        breaker.onFailure(breaker.tryAcquire());
        elapseCooldown(breaker);
    }

    private DataAccessResourceFailureException redisFailure() {
        return new DataAccessResourceFailureException("Redis unavailable");
    }
}
