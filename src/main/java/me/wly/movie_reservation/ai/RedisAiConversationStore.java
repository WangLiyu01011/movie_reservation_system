package me.wly.movie_reservation.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.wly.movie_reservation.ai.vo.AiSessionVO;
import me.wly.movie_reservation.common.exception.*;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import static me.wly.movie_reservation.common.redis.RedisScriptLoader.script;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisAiConversationStore {
    private static final RedisScript<Long> CREATE = script("redis/ai-session-create.lua", Long.class);
    @SuppressWarnings("rawtypes") private static final RedisScript<List> BEGIN = script("redis/ai-session-begin.lua", List.class);
    @SuppressWarnings("rawtypes") private static final RedisScript<List> READ = script("redis/ai-session-read.lua", List.class);
    private static final RedisScript<String> COMMIT = script("redis/ai-session-commit.lua", String.class);
    private static final RedisScript<Long> DELETE = script("redis/ai-session-delete.lua", Long.class);
    private static final RedisScript<Long> RELEASE = script("redis/lock-release-request.lua", Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final AiConversationProperties properties;
    private final Clock businessClock;

    public AiSessionVO create(String owner) {
        String id = UUID.randomUUID().toString();
        try {
            Long result = redis.execute(CREATE, List.of(key(id)), owner, encode(AiConversationState.empty()), ttl());
            if (!Long.valueOf(1).equals(result)) throw unavailable();
            return new AiSessionVO(id, expiresAt(), List.of());
        } catch (DataAccessException exception) { throw unavailable(); }
    }

    public Lease begin(String id, String owner) {
        String token = UUID.randomUUID().toString();
        try {
            List<?> result = redis.execute(BEGIN, List.of(key(id), lockKey(id)), owner, token,
                    Long.toString(properties.getLockTtl().toMillis()), ttl());
            requireResult(result);
            if ("BUSY".equals(result.getFirst())) throw new BusinessException(ResultCode.AI_SESSION_BUSY);
            try {
                return new Lease(normalize(id), owner, token, result.get(1).toString(), decode(result.get(2).toString()));
            } catch (RuntimeException exception) {
                release(id, token);
                throw exception;
            }
        } catch (DataAccessException exception) { throw unavailable(); }
    }

    public Instant commit(Lease lease, AiConversationState state) {
        String json = encode(state);
        if (json.getBytes(StandardCharsets.UTF_8).length > properties.getMaxSessionBytes()) {
            throw new BusinessException(ResultCode.AI_SESSION_LIMIT);
        }
        try {
            String result = redis.execute(COMMIT, List.of(key(lease.sessionId()), lockKey(lease.sessionId())),
                    lease.owner(), lease.token(), lease.version(), json, ttl());
            if ("NOT_FOUND".equals(result)) throw new BusinessException(ResultCode.AI_SESSION_NOT_FOUND);
            if ("STALE".equals(result)) throw new BusinessException(ResultCode.AI_SESSION_BUSY,
                    "AI generation lease expired; response was not committed");
            if (!"OK".equals(result)) throw unavailable();
            return expiresAt();
        } catch (DataAccessException exception) { throw unavailable(); }
    }

    public AiSessionVO read(String id, String owner) {
        try {
            List<?> result = redis.execute(READ, List.of(key(id)), owner);
            requireResult(result);
            long remaining = Long.parseLong(result.get(1).toString());
            if (remaining < 0) throw new BusinessException(ResultCode.AI_SESSION_NOT_FOUND);
            return new AiSessionVO(normalize(id), businessClock.instant().plusMillis(remaining), decode(result.get(2).toString()).turns());
        } catch (DataAccessException exception) { throw unavailable(); }
    }

    public void delete(String id, String owner) {
        try { redis.execute(DELETE, List.of(key(id), lockKey(id)), owner); }
        catch (DataAccessException exception) { throw unavailable(); }
    }
    public void release(Lease lease) { release(lease.sessionId(), lease.token()); }
    private void release(String id, String token) {
        try { redis.execute(RELEASE, List.of(lockKey(id)), token); }
        catch (DataAccessException exception) { log.warn("Cannot release AI generation lease; it will expire"); }
    }
    private String encode(AiConversationState state) {
        try { return mapper.writeValueAsString(state); }
        catch (RuntimeException exception) { throw unavailable(); }
    }
    private AiConversationState decode(String json) {
        try { return Objects.requireNonNull(mapper.readValue(json, AiConversationState.class)); }
        catch (RuntimeException exception) { throw unavailable(); }
    }
    private void requireResult(List<?> result) {
        if (result == null || result.isEmpty()) throw unavailable();
        if ("NOT_FOUND".equals(result.getFirst())) throw new BusinessException(ResultCode.AI_SESSION_NOT_FOUND);
    }
    public Instant expiresAt() { return businessClock.instant().plus(properties.getTtl()); }
    private String ttl() { return Long.toString(properties.getTtl().toMillis()); }
    private String normalize(String id) { return id.toLowerCase(Locale.ROOT); }
    private String key(String id) { return "ai:session:{" + normalize(id) + "}"; }
    private String lockKey(String id) { return key(id) + ":lock"; }
    private BusinessException unavailable() { return new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "AI conversation store unavailable; retry later"); }
    public record Lease(String sessionId, String owner, String token, String version, AiConversationState state) { }
}
