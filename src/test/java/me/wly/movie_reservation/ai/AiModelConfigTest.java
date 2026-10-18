package me.wly.movie_reservation.ai;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.chat.Capability;
import jakarta.validation.Validation;
import me.wly.movie_reservation.movie.MovieService;
import me.wly.movie_reservation.showtime.ShowtimeQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Exercises production configuration and actual HTTP JSON serialization, without a paid model call. */
class AiModelConfigTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final Queue<JsonNode> requests = new ConcurrentLinkedQueue<>();
    private final Queue<Map<String, Object>> replies = new ConcurrentLinkedQueue<>();
    private final AiConversationProperties properties = new AiConversationProperties();
    private HttpServer server;

    @BeforeEach
    void startLocalProvider() throws Exception {
        properties.setModelTimeout(Duration.ofSeconds(5));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                requests.add(json.readTree(exchange.getRequestBody().readAllBytes()));
                var reply = replies.remove();
                byte[] body = json.writeValueAsString(Map.of(
                        "id", "chatcmpl-test", "object", "chat.completion", "created", 0,
                        "model", "test-model", "choices", List.of(reply)))
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopLocalProvider() {
        if (server != null) server.stop(0);
    }

    @Test
    void productionModelSendsJsonObjectAndParsesOrdinaryConversation() {
        enqueueAnswer(generalAnswer());
        var model = model();

        assertThat(model.supportedCapabilities()).doesNotContain(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
        var invocation = assistant(model).start("[]");
        var result = invocation.assistant().chat("我想在杭州看电影，先记住这个城市，暂时不用查询。");

        assertThat(result.content().message()).isEqualTo("好的，已记住杭州。");
        assertThat(result.content().movies()).isEmpty();
        assertThat(result.content().showtimes()).isEmpty();
        assertThat(requests).hasSize(1);
        assertJsonMode(requests.element());
        assertThat(requests.element().path("messages").toString()).contains("最终回答只能是一个JSON对象");
    }

    @Test
    void jsonModeIsPreservedAfterToolExecution() {
        replies.add(Map.of("index", 0, "finish_reason", "tool_calls", "message", Map.of(
                "role", "assistant", "tool_calls", List.of(Map.of(
                        "id", "time-1", "type", "function", "function", Map.of(
                                "name", "get_current_time", "arguments", "{}"))))));
        enqueueAnswer(generalAnswer());

        var result = assistant(model()).start("[]").assistant().chat("现在几点？");

        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(requests).hasSize(2).allSatisfy(this::assertJsonMode);
        assertThat(requests.stream().toList().getLast().path("messages").toString()).contains("time-1");
    }

    @Test
    void jsonModeStillRejectsBusinessInvalidJson() {
        enqueueAnswer("""
                {"type":"GENERAL","message":"回答","movies":[],"showtimes":[],"missingFields":["CITY"]}
                """);
        var result = assistant(model()).start("[]").assistant().chat("你好");

        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var assembler = new AiResponseAssembler(validation.getValidator());
            assertThatThrownBy(() -> assembler.assemble(result))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertJsonMode(requests.element());
    }

    private dev.langchain4j.model.chat.ChatModel model() {
        return new AiModelConfig().movieChatModel("test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-model", properties);
    }

    private AiAssistantFactory assistant(dev.langchain4j.model.chat.ChatModel model) {
        var tools = new AiTools(mock(MovieService.class), mock(ShowtimeQueryService.class), Clock.systemUTC(),
                mock(me.wly.movie_reservation.theater.AreaSearchService.class), mock(me.wly.movie_reservation.theater.TheaterService.class));
        return new AiAssistantFactory(model, tools, properties);
    }

    private void assertJsonMode(JsonNode request) {
        assertThat(request.path("response_format")).isEqualTo(json.readTree("{\"type\":\"json_object\"}"));
        assertThat(request.path("model").asString()).isEqualTo("test-model");
        assertThat(request.path("temperature").asDouble()).isEqualTo(1.0);
    }

    private void enqueueAnswer(String answer) {
        replies.add(Map.of("index", 0, "finish_reason", "stop",
                "message", Map.of("role", "assistant", "content", answer)));
    }

    private String generalAnswer() {
        return """
                {"type":"GENERAL","message":"好的，已记住杭州。","movies":[],"showtimes":[],"missingFields":[]}
                """;
    }
}
