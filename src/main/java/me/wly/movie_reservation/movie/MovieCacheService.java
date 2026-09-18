package me.wly.movie_reservation.movie;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.wly.movie_reservation.movie.vo.MovieDetailVO;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import static me.wly.movie_reservation.common.util.RedisScriptLoader.script;


@Slf4j
@Service
@RequiredArgsConstructor
public class MovieCacheService {
    private static final String MOVIE_DETAIL_KEY_PREFIX = "movie:v1:detail:";
    private static final String MOVIE_LOCK_PREFIX = "lock:movie:v1:detail:";
    private static final String NULL_VALUE = "__NULL__";
    private static final Duration REBUILD_LOCK_TTL = Duration.ofSeconds(10);
    private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = script("redis/lock-release-request.lua");

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private enum LockResult {
        ACQUIRED,
        BUSY,
        REDIS_UNAVAILABLE
    }

    private record CacheLookup(boolean found, MovieDetailVO movie) {
        private static CacheLookup hit(MovieDetailVO movie) {
            return new CacheLookup(true, movie);
        }

        private static CacheLookup cachedNotFound() {
            return new CacheLookup(true, null);
        }

        private static CacheLookup miss() {
            return new CacheLookup(false, null);
        }
    }

    public MovieDetailVO getOrLoadDetail(
            String imdbId,
            Supplier<MovieDetailVO> databaseLoader
    ) {
        String cacheKey = detailKey(imdbId);
        CacheLookup lookup = readDetail(cacheKey);
        if (lookup.found()) {
            return lookup.movie();
        }

        return rebuildDetail(imdbId, cacheKey, databaseLoader);
    }

    public void evictDetail(String imdbId) {
        deleteCache(detailKey(imdbId));
    }

    private MovieDetailVO rebuildDetail(
            String imdbId,
            String cacheKey,
            Supplier<MovieDetailVO> databaseLoader
    ) {
        String lockKey = MOVIE_LOCK_PREFIX + imdbId;
        String lockToken = UUID.randomUUID().toString();
        LockResult lockResult = tryAcquireLock(lockKey, lockToken);

        if (lockResult == LockResult.REDIS_UNAVAILABLE) {
            return loadAndCache(cacheKey, databaseLoader);
        }

        if (lockResult == LockResult.ACQUIRED) {
            try {
                CacheLookup secondLookup = readDetail(cacheKey);
                if (secondLookup.found()) {
                    return secondLookup.movie();
                }
                return loadAndCache(cacheKey, databaseLoader);
            } finally {
                releaseLock(lockKey, lockToken);
            }
        }

        for (int retry = 0; retry < 5; retry++) {
            if (!sleepBriefly()) {
                break;
            }
            CacheLookup retryLookup = readDetail(cacheKey);
            if (retryLookup.found()) {
                return retryLookup.movie();
            }
        }

        return loadAndCache(cacheKey, databaseLoader);
    }

    private MovieDetailVO loadAndCache(
            String cacheKey,
            Supplier<MovieDetailVO> databaseLoader
    ) {
        MovieDetailVO movie = databaseLoader.get();
        if (movie == null) {
            setNullCache(cacheKey);
        } else {
            setMovieCache(cacheKey, movie);
        }
        return movie;
    }

    private CacheLookup readDetail(String key) {
        String cachedJson = getCache(key);
        if (cachedJson == null) {
            return CacheLookup.miss();
        }
        if (NULL_VALUE.equals(cachedJson)) {
            return CacheLookup.cachedNotFound();
        }

        MovieDetailVO movie = deserializeMovie(key, cachedJson);
        return movie == null ? CacheLookup.miss() : CacheLookup.hit(movie);
    }

    private LockResult tryAcquireLock(String lockKey, String token) {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, REBUILD_LOCK_TTL);
            return Boolean.TRUE.equals(acquired)
                    ? LockResult.ACQUIRED
                    : LockResult.BUSY;
        } catch (DataAccessException exception) {
            log.warn("Redis cache rebuild lock failed, key={}", lockKey, exception);
            return LockResult.REDIS_UNAVAILABLE;
        }
    }

    private void releaseLock(String lockKey, String token) {
        try {
            stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), token);
        } catch (DataAccessException exception) {
            log.warn("Redis cache rebuild unlock failed, key={}", lockKey, exception);
        }
    }

    private String getCache(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (DataAccessException exception) {
            log.warn("Redis read failed, key={}", key, exception);
            return null;
        }
    }

    private MovieDetailVO deserializeMovie(String key, String json) {
        try {
            return objectMapper.readValue(json, MovieDetailVO.class);
        } catch (JacksonException exception) {
            log.warn("Invalid movie cache, key={}", key, exception);
            deleteCache(key);
            return null;
        }
    }

    private void setMovieCache(String key, MovieDetailVO movie) {
        try {
            String json = objectMapper.writeValueAsString(movie);
            long randomSeconds = ThreadLocalRandom.current().nextLong(0, 301);
            Duration ttl = Duration.ofMinutes(30).plusSeconds(randomSeconds);
            stringRedisTemplate.opsForValue().set(key, json, ttl);
        } catch (JacksonException exception) {
            log.warn("Movie serialization failed, key={}", key, exception);
        } catch (DataAccessException exception) {
            log.warn("Redis write failed, key={}", key, exception);
        }
    }

    private void setNullCache(String key) {
        try {
            stringRedisTemplate.opsForValue().set(key, NULL_VALUE, Duration.ofSeconds(60));
        } catch (DataAccessException exception) {
            log.warn("Redis null cache write failed, key={}", key, exception);
        }
    }

    private void deleteCache(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (DataAccessException exception) {
            log.warn("Redis cache delete failed, key={}", key, exception);
        }
    }

    private String detailKey(String imdbId) {
        return MOVIE_DETAIL_KEY_PREFIX + imdbId;
    }

    private boolean sleepBriefly() {
        try {
            Thread.sleep(50);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
