package me.wly.movie_reservation.order;

import me.wly.movie_reservation.common.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCacheServiceTest {
    private final java.time.Clock businessClock = java.time.Clock.fixed(
            java.time.Instant.parse("2030-01-01T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private ZSetOperations<String, String> zSet;
    private OrderRedisProperties properties;
    private OrderCacheService service;

    @BeforeEach
    void setUp() {
        properties = new OrderRedisProperties();
        service = new OrderCacheService(businessClock, redis, properties);
    }

    @Test
    void acquireRequest_setsUniqueTokenAndTtl() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("order:idem:{10}:request-1"), anyString(), eq(Duration.ofSeconds(60))))
                .thenReturn(true);

        var permit = service.acquireRequest(10L, "request-1");

        assertEquals(OrderCacheService.RequestState.ACQUIRED, permit.state());
        assertFalse(permit.token().isBlank());
        verify(values).setIfAbsent(permit.key(), permit.token(), Duration.ofSeconds(60));
    }

    @Test
    void acquireRequest_reportsBusyWithoutOverwritingOwner() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertEquals(OrderCacheService.RequestState.BUSY, service.acquireRequest(10L, "request-1").state());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void acquireRequest_degradesWhenRedisIsDown() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertEquals(OrderCacheService.RequestState.REDIS_UNAVAILABLE,
                service.acquireRequest(10L, "request-1").state());
    }

    @Test
    void disabledAdmissionControl_doesNotConnectToRedis() {
        properties.setEnabled(false);

        assertEquals(OrderCacheService.RequestState.REDIS_UNAVAILABLE,
                service.acquireRequest(10L, "request-1").state());
        assertDoesNotThrow(() -> service.checkRateLimit(10L));
        assertDoesNotThrow(() -> service.addOrderToZSet(50L, LocalDateTime.now(businessClock)));
        verifyNoInteractions(redis);
    }

    @Test
    void checkRateLimit_allowsRequestAndPassesSlidingWindowParameters() {
        when(redis.execute(any(RedisScript.class), eq(List.of("order:rate:create:{10}")),
                eq("10000"), eq("3"), anyString())).thenReturn(0L);

        assertDoesNotThrow(() -> service.checkRateLimit(10L));
        verify(redis).execute(any(RedisScript.class), eq(List.of("order:rate:create:{10}")),
                eq("10000"), eq("3"), anyString());
    }

    @Test
    void checkRateLimit_rejectsWithRoundedRetryAfter() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1501L);

        RateLimitException exception = assertThrows(RateLimitException.class,
                () -> service.checkRateLimit(10L));

        assertEquals(2, exception.getRetryAfterSeconds());
    }

    @Test
    void checkRateLimit_degradesWhenRedisIsDown() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertDoesNotThrow(() -> service.checkRateLimit(10L));
    }

    @Test
    void releaseRequest_executesOwnerCheckedLua() {
        var permit = new OrderCacheService.RequestPermit("order:idem:{10}:request-1", "owner-token",
                OrderCacheService.RequestState.ACQUIRED);

        service.releaseRequest(permit);

        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(scriptCaptor.capture(), eq(List.of(permit.key())), eq(permit.token()));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("ARGV[1]"));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("DEL"));
    }

    @Test
    void releaseRequest_doesNotReleaseSomeoneElsesPermit() {
        service.releaseRequest(new OrderCacheService.RequestPermit("key", "token", OrderCacheService.RequestState.BUSY));
        service.releaseRequest(new OrderCacheService.RequestPermit("key", "token", OrderCacheService.RequestState.REDIS_UNAVAILABLE));
        verifyNoInteractions(redis);
    }

    @Test
    void releaseRequest_failureDoesNotHideSuccessfulOrder() {
        var permit = new OrderCacheService.RequestPermit("key", "token", OrderCacheService.RequestState.ACQUIRED);
        when(redis.execute(any(RedisScript.class), eq(List.of("key")), eq("token")))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertDoesNotThrow(() -> service.releaseRequest(permit));
    }

    @Test
    void addOrderToZSet_usesOrderIdAndShanghaiExpirationMillis() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 23, 21, 5, 30);
        double expectedScore = expiresAt.atZone(ZoneId.of("Asia/Shanghai"))
                .toInstant().toEpochMilli();
        when(redis.opsForZSet()).thenReturn(zSet);

        service.addOrderToZSet(500L, expiresAt);

        verify(zSet).add("order:expiry", "500", expectedScore);
    }

    @Test
    void addOrderToZSet_redisFailureDoesNotHideCommittedOrder() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 23, 21, 5, 30);
        when(redis.opsForZSet()).thenReturn(zSet);
        when(zSet.add(eq("order:expiry"), eq("500"), anyDouble()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertDoesNotThrow(() -> service.addOrderToZSet(500L, expiresAt));
    }

    @Test
    void findExpiredOrderIds_returnsEmptyListWhenRedisReturnsNull() {
        when(redis.opsForZSet()).thenReturn(zSet);
        when(zSet.rangeByScore(eq("order:expiry"), eq(Double.NEGATIVE_INFINITY),
                anyDouble(), eq(0L), eq(20L))).thenReturn(null);

        assertEquals(List.of(), service.findExpiredOrderIds());
    }

    @Test
    void findExpiredOrderIds_convertsRedisMembersToLongs() {
        when(redis.opsForZSet()).thenReturn(zSet);
        when(zSet.rangeByScore(eq("order:expiry"), eq(Double.NEGATIVE_INFINITY),
                anyDouble(), eq(0L), eq(20L)))
                .thenReturn(new LinkedHashSet<>(List.of("501", "502")));

        assertEquals(List.of(501L, 502L), service.findExpiredOrderIds());
        verify(zSet).rangeByScore("order:expiry", Double.NEGATIVE_INFINITY,
                (double) businessClock.millis(), 0L, 20L);
    }
}
