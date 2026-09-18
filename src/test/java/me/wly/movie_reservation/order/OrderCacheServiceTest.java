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
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCacheServiceTest {
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    private OrderRedisProperties properties;
    private OrderCacheService service;

    @BeforeEach
    void setUp() {
        properties = new OrderRedisProperties();
        service = new OrderCacheService(redis, properties);
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
}
