package me.wly.movie_reservation.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.wly.movie_reservation.common.exception.RateLimitException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static me.wly.movie_reservation.common.util.RedisScriptLoader.script;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCacheService {
    private static final RedisScript<Long> RELEASE_SCRIPT = script("redis/lock-release-request.lua", Long.class);
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = script("redis/order-rate-limit.lua", Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final OrderRedisProperties properties;

    public enum RequestState {
        ACQUIRED,
        BUSY,
        REDIS_UNAVAILABLE
    }

    public record RequestPermit(String key, String token, RequestState state) { }

    public RequestPermit acquireRequest(Long userId, String requestId) {
        String key = "order:idem:{" + userId + "}:" + requestId;
        String token = UUID.randomUUID().toString();
        if (!properties.isEnabled()) {
            return new RequestPermit(key, token, RequestState.REDIS_UNAVAILABLE);
        }
        try {
            // 尝试设置redis锁
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, token, properties.getRequestLockTtl());
            RequestState state = acquired == null ? RequestState.REDIS_UNAVAILABLE
                    : acquired ? RequestState.ACQUIRED : RequestState.BUSY;
            return new RequestPermit(key, token, state);
        } catch (DataAccessException exception) {
            log.warn("Redis order deduplication unavailable; falling back to MySQL, userId={}", userId, exception);
            return new RequestPermit(key, token, RequestState.REDIS_UNAVAILABLE);
        }
    }

    public void checkRateLimit(Long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        Long retryAfterMillis;
        try {
            retryAfterMillis = stringRedisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of("order:rate:create:{" + userId + "}"),
                    String.valueOf(properties.getRateLimitWindow().toMillis()),
                    String.valueOf(properties.getRateLimitMaxRequests()),
                    UUID.randomUUID().toString()
            );
        } catch (DataAccessException exception) {
            log.warn("Redis order rate limiting unavailable; allowing database fallback, userId={}", userId, exception);
            return;
        }
        // 返回了重试时间说明超限，抛出异常
        if (retryAfterMillis != null && retryAfterMillis > 0) {
            throw new RateLimitException(retryAfterMillis);
        }
    }

    public void releaseRequest(RequestPermit permit) {
        if (permit.state() != RequestState.ACQUIRED) {
            return;
        }
        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(permit.key()), permit.token());
        } catch (DataAccessException exception) {
            // 即使主动release失败，超过ttl也会自动release
            log.warn("Redis order request unlock failed, key={}", permit.key(), exception);
        }
    }

    public void addOrderToZSet(Long orderId, LocalDateTime expireAt) {
        if(!properties.isEnabled()) {
            return;
        }
        String key = "order:expiry";
        try {
            double milliScore = expireAt.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
            stringRedisTemplate.opsForZSet().add(key, orderId.toString(), milliScore);
        } catch (DataAccessException exception) {
            log.warn("Redis order expire set unavailable", exception);
        }
    }

    public void deleteOrderFromZSet(String orderId) {
        if(!properties.isEnabled()) {
            return;
        }
        String key = "order:expiry";
        try {
            stringRedisTemplate.opsForZSet().remove(
                    key,
                    orderId
            );
        } catch (DataAccessException exception) {
            log.warn("Redis order expire set unavailable", exception);
        }
    }

    public List<Long> findExpiredOrderIds() {
        if(!properties.isEnabled()) {
            return new ArrayList<>();
        }
        double now = LocalDateTime.now().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        String key = "order:expiry";
        try {

            Set<String> expiredIds = stringRedisTemplate.opsForZSet().rangeByScore(
                    key, Double.NEGATIVE_INFINITY, now, 0, properties.getBatchSizeForExpiration()
            );
            if (expiredIds == null || expiredIds.isEmpty()) {
                return List.of();
            }
            return expiredIds.stream().map(Long::valueOf).toList();
        } catch (DataAccessException exception) {
            log.warn("Unable to get expired orders from redis", exception);
            return new ArrayList<>();
        }
    }
}
