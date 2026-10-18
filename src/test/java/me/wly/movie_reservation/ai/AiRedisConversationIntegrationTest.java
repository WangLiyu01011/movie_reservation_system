package me.wly.movie_reservation.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.validation.*;
import me.wly.movie_reservation.ai.dto.AiRequestDTO;
import me.wly.movie_reservation.common.exception.*;
import me.wly.movie_reservation.movie.MovieService;
import me.wly.movie_reservation.showtime.ShowtimeQueryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Lua and LangChain4j memory, disposable Redis, scripted model, no MySQL or model network calls. */
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_INTEGRATION_TESTS", matches = "true")
class AiRedisConversationIntegrationTest {
    @TempDir static Path directory;
    private static Process server;
    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;
    private static ValidatorFactory validators;
    private final Clock clock = Clock.systemUTC();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private AiConversationProperties properties;
    private RedisAiConversationStore store;

    @BeforeAll static void startRedis() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        server = new ProcessBuilder(System.getenv().getOrDefault("REDIS_SERVER_EXECUTABLE", "redis-server"),
                "--bind", "127.0.0.1", "--port", Integer.toString(port), "--save", "", "--appendonly", "no",
                "--dir", directory.toString()).redirectErrorStream(true).redirectOutput(directory.resolve("redis.log").toFile()).start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean ready = false;
        while (server.isAlive() && System.nanoTime() < deadline) {
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 100); ready = true; break;
            } catch (java.io.IOException exception) { Thread.sleep(20); }
        }
        assertThat(ready).as("Disposable Redis starts").isTrue();
        connections = new LettuceConnectionFactory("127.0.0.1", port);
        connections.afterPropertiesSet(); connections.start();
        redis = new StringRedisTemplate(connections); redis.afterPropertiesSet();
        validators = Validation.buildDefaultValidatorFactory();
    }
    @AfterAll static void stopRedis() throws Exception {
        if (validators != null) validators.close();
        if (connections != null) connections.destroy();
        if (server != null) {
            server.destroy();
            if (!server.waitFor(2, TimeUnit.SECONDS)) { server.destroyForcibly(); server.waitFor(2, TimeUnit.SECONDS); }
        }
    }
    @BeforeEach void setUp() {
        properties = new AiConversationProperties();
        store = new RedisAiConversationStore(redis, mapper, properties, clock);
    }
    private AiApplicationService application(ScriptedModel model) {
        return new AiApplicationService(store, new AiAssistantFactory(model,
                new AiTools(mock(MovieService.class), mock(ShowtimeQueryService.class), clock,
                        mock(me.wly.movie_reservation.theater.AreaSearchService.class),
                        mock(me.wly.movie_reservation.theater.TheaterService.class)), properties),
                new AiResponseAssembler(validators.getValidator()), properties, validators.getValidator(), clock);
    }
    private AiRequestDTO request(String session, String request, String text) { return new AiRequestDTO(session, request, text); }
    private String key(String id) { return "ai:session:{" + id + "}"; }
    private void assertCode(ResultCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.resultCode).isEqualTo(code));
    }

    @Test void createsSessionWithTtlAndHidesItFromAnotherUser() {
        var session = store.create("alice");
        assertThat(redis.getExpire(key(session.sessionId()), TimeUnit.MILLISECONDS)).isBetween(1L, properties.getTtl().toMillis());
        assertThat(store.read(session.sessionId(), "alice").turns()).isEmpty();
        assertCode(ResultCode.AI_SESSION_NOT_FOUND, () -> store.read(session.sessionId(), "bob"));
        assertCode(ResultCode.AI_SESSION_NOT_FOUND, () -> store.begin(session.sessionId(), "bob"));
        store.delete(session.sessionId(), "bob");
        assertThat(store.read(session.sessionId(), "alice")).isNotNull();
    }

    @Test void multipleInstancesRestoreMemoryAndReplayRequestsWithoutCallingModelAgain() {
        var session = store.create("alice"); var first = new ScriptedModel();
        application(first).chat(request(session.sessionId(), "r1", "我在杭州"), "alice");
        var second = new ScriptedModel(); var anotherInstance = application(second);
        var result = anotherInstance.chat(request(session.sessionId(), "r2", "推荐明天下午"), "alice");
        assertThat(second.requests.getFirst().messages()).anySatisfy(message -> assertThat(message.toString()).contains("我在杭州"));
        var replay = anotherInstance.chat(request(session.sessionId(), "r2", "推荐明天下午"), "alice");
        assertThat(replay.response()).isEqualTo(result.response());
        assertThat(second.requests).hasSize(1);
        assertThat(store.read(session.sessionId(), "alice").turns()).hasSize(2);
    }

    @Test void twoSessionsNeverShareModelHistory() {
        var a = store.create("alice"); var b = store.create("bob"); var model = new ScriptedModel(); var service = application(model);
        service.chat(request(a.sessionId(), "r1", "alice-secret-marker"), "alice");
        service.chat(request(b.sessionId(), "r1", "bob-question"), "bob");
        assertThat(model.requests.getLast().messages()).noneSatisfy(message -> assertThat(message.toString()).contains("alice-secret-marker"));
        assertCode(ResultCode.AI_SESSION_NOT_FOUND, () -> service.chat(request(a.sessionId(), "r2", "偷看"), "bob"));
    }

    @Test void sameRequestWithDifferentContentIsRejected() {
        var session = store.create("alice"); var model = new ScriptedModel(); var service = application(model);
        service.chat(request(session.sessionId(), "r1", "原问题"), "alice");
        assertCode(ResultCode.AI_REQUEST_CONFLICT, () -> service.chat(request(session.sessionId(), "r1", "另一个问题"), "alice"));
        assertThat(model.requests).hasSize(1);
    }

    @Test void failedModelOutputDoesNotPolluteHistoryAndSameRequestCanRetry() {
        var session = store.create("alice"); var model = new ScriptedModel("not-json"); var service = application(model);
        assertCode(ResultCode.INTERNAL_SERVER_ERROR, () -> service.chat(request(session.sessionId(), "r1", "问题"), "alice"));
        assertThat(store.read(session.sessionId(), "alice").turns()).isEmpty();
        assertThat(redis.hasKey(key(session.sessionId()) + ":lock")).isFalse();
        service.chat(request(session.sessionId(), "r1", "问题"), "alice");
        assertThat(store.read(session.sessionId(), "alice").turns()).hasSize(1);
        assertThat(model.requests.getLast().messages()).noneSatisfy(message -> assertThat(message.toString()).contains("not-json"));
    }

    @Test void concurrentWorkersOnlyAcquireOneGenerationLease() throws Exception {
        var session = store.create("alice");
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<RedisAiConversationStore.Lease>> calls = new ArrayList<>();
            for (int i = 0; i < 32; i++) calls.add(() -> {
                try { return store.begin(session.sessionId(), "alice"); }
                catch (BusinessException exception) {
                    assertThat(exception.resultCode).isEqualTo(ResultCode.AI_SESSION_BUSY); return null;
                }
            });
            var winners = new ArrayList<RedisAiConversationStore.Lease>();
            for (var future : executor.invokeAll(calls, 5, TimeUnit.SECONDS)) {
                var lease = future.get(); if (lease != null) winners.add(lease);
            }
            assertThat(winners).hasSize(1); store.release(winners.getFirst());
        }
    }

    @Test void leaseExpiryRejectsOldCommitAndOldUnlockCannotDeleteNewLease() throws Exception {
        var session = store.create("alice"); var old = store.begin(session.sessionId(), "alice");
        String lockKey = key(session.sessionId()) + ":lock";
        redis.expire(lockKey, Duration.ofMillis(30)); awaitExpiry(lockKey);
        var fresh = store.begin(session.sessionId(), "alice");
        assertCode(ResultCode.AI_SESSION_BUSY, () -> store.commit(old, AiConversationState.empty()));
        store.release(old); assertThat(redis.opsForValue().get(lockKey)).isEqualTo(fresh.token());
        store.commit(fresh, AiConversationState.empty()); assertThat(redis.hasKey(lockKey)).isFalse();
    }

    @Test void deletedSessionCannotBeResurrectedByLateResponse() {
        var session = store.create("alice"); var lease = store.begin(session.sessionId(), "alice");
        store.delete(session.sessionId(), "alice");
        assertCode(ResultCode.AI_SESSION_NOT_FOUND, () -> store.commit(lease, AiConversationState.empty()));
        assertThat(redis.hasKey(key(session.sessionId()))).isFalse();
    }

    @Test void ttlExpiryRemovesHistoryWithoutDatabaseWritesOrRecreation() throws Exception {
        var session = store.create("alice");
        redis.expire(key(session.sessionId()), Duration.ofMillis(30)); awaitExpiry(key(session.sessionId()));
        assertCode(ResultCode.AI_SESSION_NOT_FOUND, () -> store.read(session.sessionId(), "alice"));
        assertCode(ResultCode.AI_SESSION_NOT_FOUND, () -> store.begin(session.sessionId(), "alice"));
        assertThat(redis.hasKey(key(session.sessionId()))).isFalse();
    }

    @Test void acceptedChatRenewsTtlButHistoryReadDoesNot() {
        var session = store.create("alice"); String key = key(session.sessionId());
        redis.expire(key, Duration.ofSeconds(2)); store.read(session.sessionId(), "alice");
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(2000L);
        var lease = store.begin(session.sessionId(), "alice");
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isGreaterThan(60_000L); store.release(lease);
    }

    @Test void modelContextIsBoundedWhileCompletedRepliesRemainReplayable() {
        properties.setMaxMessages(8); var session = store.create("alice"); var model = new ScriptedModel(); var service = application(model);
        for (int i = 0; i < 7; i++) service.chat(request(session.sessionId(), "r" + i, "marker-" + i), "alice");
        assertThat(model.requests.getLast().messages()).hasSizeLessThanOrEqualTo(8);
        assertThat(model.requests.getLast().messages()).noneSatisfy(message -> assertThat(message.toString()).contains("marker-0"));
        service.chat(request(session.sessionId(), "r0", "marker-0"), "alice");
        assertThat(model.requests).hasSize(7); assertThat(store.read(session.sessionId(), "alice").turns()).hasSize(7);
    }

    @Test void sessionTurnLimitRejectsNewMessagesButKeepsReplay() {
        properties.setMaxTurns(1); var session = store.create("alice"); var model = new ScriptedModel(); var service = application(model);
        service.chat(request(session.sessionId(), "r1", "问题"), "alice");
        assertCode(ResultCode.AI_SESSION_LIMIT, () -> service.chat(request(session.sessionId(), "r2", "再问"), "alice"));
        service.chat(request(session.sessionId(), "r1", "问题"), "alice"); assertThat(model.requests).hasSize(1);
    }

    @Test void sessionByteLimitDoesNotSavePartialTurn() {
        properties.setMaxSessionBytes(1024); var session = store.create("alice"); var service = application(new ScriptedModel());
        assertCode(ResultCode.AI_SESSION_LIMIT, () -> service.chat(request(session.sessionId(), "r1", "x".repeat(4000)), "alice"));
        assertThat(store.read(session.sessionId(), "alice").turns()).isEmpty();
        assertThat(redis.hasKey(key(session.sessionId()) + ":lock")).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"broken-json", "null"})
    void corruptedRedisDocumentIsControlledAndLeaseIsReleased(String json) {
        var session = store.create("alice"); redis.opsForHash().put(key(session.sessionId()), "data", json);
        assertCode(ResultCode.SERVICE_UNAVAILABLE, () -> store.begin(session.sessionId(), "alice"));
        assertThat(redis.hasKey(key(session.sessionId()) + ":lock")).isFalse();
    }

    @Test void httpLifecycleCreatesChatsReadsAndDeletesUsingAuthenticatedOwner() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AiController(application(new ScriptedModel())))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/ai/sessions")).andExpect(status().isUnauthorized());
        var created = mvc.perform(post("/api/v1/ai/sessions").principal(() -> "alice"))
                .andExpect(status().isCreated()).andReturn();
        String id = mapper.readTree(created.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        mvc.perform(post("/api/v1/ai").principal(() -> "alice").contentType("application/json")
                        .content(mapper.writeValueAsString(request(id, "r1", "你好"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value(id))
                .andExpect(jsonPath("$.data.response.type").value("GENERAL"));
        mvc.perform(get("/api/v1/ai/sessions/" + id).principal(() -> "alice"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.turns.length()").value(1));
        mvc.perform(get("/api/v1/ai/sessions/" + id).principal(() -> "bob")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/ai/sessions/" + id).principal(() -> "alice")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/ai/sessions/" + id).principal(() -> "alice")).andExpect(status().isNotFound());
    }

    private void awaitExpiry(String key) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (Boolean.TRUE.equals(redis.hasKey(key)) && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(redis.hasKey(key)).isFalse();
    }
    private static class ScriptedModel implements ChatModel {
        private final Queue<String> replies;
        final List<ChatRequest> requests = new ArrayList<>();
        ScriptedModel(String... replies) { this.replies = new ArrayDeque<>(List.of(replies)); }
        @Override public Set<Capability> supportedCapabilities() { return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA); }
        @Override public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            String answer = replies.isEmpty() ? "{\"type\":\"GENERAL\",\"message\":\"你好\",\"movies\":[],\"showtimes\":[],\"missingFields\":[]}" : replies.remove();
            return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
        }
    }
}
