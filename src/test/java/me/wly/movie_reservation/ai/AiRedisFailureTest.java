package me.wly.movie_reservation.ai;

import me.wly.movie_reservation.common.exception.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiRedisFailureTest {
    @ParameterizedTest @ValueSource(strings = {"create", "begin", "read", "delete", "commit"})
    @SuppressWarnings("unchecked")
    void redisFailure_isReportedAs503WithoutFallbackOrSensitiveError(String operation) {
        var redis = mock(StringRedisTemplate.class);
        doThrow(new DataAccessResourceFailureException("private Redis endpoint"))
                .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        var store = new RedisAiConversationStore(redis, JsonMapper.builder().build(), new AiConversationProperties(), Clock.systemUTC());
        var lease = new RedisAiConversationStore.Lease("id", "alice", "token", "0", AiConversationState.empty());
        assertThatThrownBy(() -> {
            switch (operation) {
                case "create" -> store.create("alice");
                case "begin" -> store.begin("id", "alice");
                case "read" -> store.read("id", "alice");
                case "delete" -> store.delete("id", "alice");
                case "commit" -> store.commit(lease, AiConversationState.empty());
            }
        }).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.resultCode).isEqualTo(ResultCode.SERVICE_UNAVAILABLE);
            assertThat(exception.getMessage()).doesNotContain("private");
        });
        assertThatCode(() -> store.release(lease)).doesNotThrowAnyException();
    }
}
