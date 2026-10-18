package me.wly.movie_reservation.ai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import me.wly.movie_reservation.ai.dto.AiRequestDTO;
import me.wly.movie_reservation.ai.vo.AiBlock;
import me.wly.movie_reservation.ai.vo.AiResponseVO;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.GlobalExceptionHandler;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.movie.MovieService;
import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.showtime.ShowtimeQueryService;
import me.wly.movie_reservation.showtime.dto.ShowtimeCandidateDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchResultDTO;
import me.wly.movie_reservation.theater.model.HallType;
import me.wly.movie_reservation.theater.AreaSearchService;
import me.wly.movie_reservation.theater.TheaterService;
import me.wly.movie_reservation.theater.dto.AreaCandidateDTO;
import me.wly.movie_reservation.theater.dto.AreaSearchResultDTO;
import me.wly.movie_reservation.theater.dto.TheaterSearchResultDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import tools.jackson.databind.json.JsonMapper;

class AiResponseTest {
    private static ValidatorFactory factory;
    private static final String SESSION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static class TestConversation {
        final AiApplicationService application;
        int sequence;
        TestConversation(AiApplicationService application) { this.application = application; }
        AiResponseVO chat(String message) {
            return application.chat(new AiRequestDTO(SESSION_ID, "request-" + sequence++, message), "alice").response();
        }
    }
    private String body(String message) {
        return JsonMapper.builder().build().writeValueAsString(new AiRequestDTO(SESSION_ID, "http-request", message));
    }
    private final MovieService movies = mock(MovieService.class);
    private final ShowtimeQueryService showtimes = mock(ShowtimeQueryService.class);
    private final LocalDateTime start = LocalDateTime.of(2030, 1, 1, 12, 0);
    private final AreaSearchService areas = mock(AreaSearchService.class);
    private final TheaterService theaters = mock(TheaterService.class);
    private final AiTools tools = new AiTools(movies, showtimes, Clock.systemUTC(), areas, theaters);

    @BeforeAll static void openValidator() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidator() { factory.close(); }

    @Test
    void actualToolExecutionAndStructuredParsingProduceHttpCardsAndPagination() throws Exception {
        when(showtimes.searchCandidates(any())).thenReturn(new ShowtimeSearchResultDTO(List.of(candidate()), 0, 10, true));
        ScriptedModel model = new ScriptedModel(toolCall(), AiMessage.from(answer("SHOWTIME_OPTIONS",
                "[{\"showtimeId\":1001,\"reason\":\"时间合适\"}]", "[]")));
        var mvc = MockMvcBuilders.standaloneSetup(new AiController(service(model).application))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/ai").principal(() -> "alice").contentType("application/json")
                        .content(body("帮我查询场次")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.response.schemaVersion").value(2))
                .andExpect(jsonPath("$.data.response.type").value("SHOWTIME_OPTIONS"))
                .andExpect(jsonPath("$.data.response.blocks[0].type").value("TEXT"))
                .andExpect(jsonPath("$.data.response.blocks[0].data.text").value("查询说明"))
                .andExpect(jsonPath("$.data.response.blocks[1].type").value("SHOWTIME_CARD"))
                .andExpect(jsonPath("$.data.response.blocks[1].data.showtime.price").value(45.50))
                .andExpect(jsonPath("$.data.response.blocks[1].data.showtime.availableSeatCount").value(7))
                .andExpect(jsonPath("$.data.response.blocks[1].data.reason").value("时间合适"))
                .andExpect(jsonPath("$.data.response.blocks[2].type").value("NOTICE"))
                .andExpect(jsonPath("$.data.response.showtimeQueries[0].hasMore").value(true))
                .andExpect(jsonPath("$.data.response.showtimeQueries[0].returnedShowtimeIds[0]").value(1001))
                .andExpect(jsonPath("$.data.response.blocks[2].data.text").isNotEmpty());
        assertThat(model.requests).hasSize(2);
        assertThat(model.requests.getLast().responseFormat().jsonSchema()).isNotNull();
    }

    @Test
    void finalJsonContractIsSentEvenForPlainConversation() {
        String reply = """
                {"type":"GENERAL","message":"好的，已记住您想在杭州看电影。",
                 "movies":[],"showtimes":[],"missingFields":[]}
                """;
        var model = new ScriptedModel(AiMessage.from(reply));

        var response = service(model).chat("我想在杭州看电影，先记住这个城市，暂时不用查询。");

        assertThat(response.type()).isEqualTo(me.wly.movie_reservation.ai.protocol.AiResponseType.GENERAL);
        assertThat(response.blocks()).containsExactly(new AiBlock.Text("text-0",
                new AiBlock.TextData("好的，已记住您想在杭州看电影。")));
        assertThat(model.requests).hasSize(1);
        assertThat(model.requests.getFirst().messages()).anySatisfy(message -> {
            assertThat(message).isInstanceOf(dev.langchain4j.data.message.SystemMessage.class);
            var prompt = ((dev.langchain4j.data.message.SystemMessage) message).text();
            assertThat(prompt).contains("最终回答只能是一个JSON对象",
                    "type、message、movies、showtimes、missingFields", "\"type\":\"GENERAL\"");
        });
        assertThat(model.requests.getFirst().responseFormat().jsonSchema()).isNotNull();
        verifyNoInteractions(movies, showtimes);
    }

    @Test
    void resolvesCityAndDistrictThenLooksUpTheatersThroughActualToolLoop() {
        when(areas.search("汕头", 1, null)).thenReturn(resolvedCity());
        when(areas.search("龙湖", 2, 440500)).thenReturn(resolvedDistrict());
        when(theaters.searchTheaters(440500, 440507)).thenReturn(
                new TheaterSearchResultDTO(440500, 440507, List.of(), false));
        var model = new ScriptedModel(
                call("city-1", "search_areas", "{\"keyword\":\"汕头\",\"level\":1}"),
                call("district-1", "search_areas", "{\"keyword\":\"龙湖\",\"level\":2,\"parentId\":440500}"),
                call("theaters-1", "get_theaters", "{\"cityId\":440500,\"districtId\":440507}"),
                AiMessage.from("""
                        {"type":"GENERAL","message":"本系统暂未收录龙湖区影院。","movies":[],"showtimes":[],"missingFields":[]}
                        """));
        var result = service(model).chat("汕头龙湖有哪些影院？");
        assertThat(result.type()).isEqualTo(me.wly.movie_reservation.ai.protocol.AiResponseType.GENERAL);
        verify(areas).search("汕头", 1, null);
        verify(areas).search("龙湖", 2, 440500);
        verify(theaters).searchTheaters(440500, 440507);
        verifyNoInteractions(movies, showtimes);
        assertThat(model.requests).hasSize(4);
    }

    @Test
    void unmatchedAreaCanAskForClarificationWithoutMovieQueryEvidence() {
        when(areas.search("未收录市", 1, null)).thenReturn(new AreaSearchResultDTO(
                "未收录市", AreaSearchResultDTO.Status.NOT_FOUND, List.of(), false));
        var model = new ScriptedModel(call("city-1", "search_areas", "{\"keyword\":\"未收录市\",\"level\":1}"),
                AiMessage.from(answer("CLARIFICATION", "[]", "[\"CITY\"]")));
        assertThat(service(model).chat("未收录市有什么电影？").type())
                .isEqualTo(me.wly.movie_reservation.ai.protocol.AiResponseType.CLARIFICATION);
        verifyNoInteractions(movies, showtimes, theaters);
    }

    @Test
    void ambiguousDistrictCanAskForCityWithoutQueryingTheaters() {
        when(areas.search("西湖区", 2, null)).thenReturn(new AreaSearchResultDTO("西湖区",
                AreaSearchResultDTO.Status.AMBIGUOUS, List.of(
                new AreaCandidateDTO(330106, "西湖区", 2, 330100, "杭州市"),
                new AreaCandidateDTO(360103, "西湖区", 2, 360100, "南昌市")), false));
        var model = new ScriptedModel(call("district-1", "search_areas", "{\"keyword\":\"西湖区\",\"level\":2}"),
                AiMessage.from(answer("CLARIFICATION", "[]", "[\"CITY\",\"DISTRICT\"]")));
        assertThat(service(model).chat("西湖区有哪些影院？").type())
                .isEqualTo(me.wly.movie_reservation.ai.protocol.AiResponseType.CLARIFICATION);
        verifyNoInteractions(movies, showtimes, theaters);
    }

    @Test
    void sequentialLocationAndShowtimeLookupFitsTheExpandedToolBudget() {
        when(areas.search("汕头", 1, null)).thenReturn(resolvedCity());
        when(areas.search("龙湖", 2, 440500)).thenReturn(resolvedDistrict());
        when(movies.getShowingMoviesOn(start)).thenReturn(List.of(new Movie(1, "tt1", start.minusDays(1), null,
                "电影", "剧情简介", List.of("剧情"), "中文", "https://example.com/poster.jpg")));
        var actual = new ShowtimeCandidateDTO(1001L, 1, "tt1", "电影", 2, "影院", "地址",
                440500, "汕头市", 440507, "龙湖区", 3, "影厅", HallType.IMAX,
                start, start.plusHours(2), new BigDecimal("45.50"), 7);
        when(showtimes.searchCandidates(any())).thenReturn(new ShowtimeSearchResultDTO(List.of(actual), 0, 10, false));
        var model = new ScriptedModel(
                call("time-1", "get_current_time", "{}"),
                call("city-1", "search_areas", "{\"keyword\":\"汕头\",\"level\":1}"),
                call("district-1", "search_areas", "{\"keyword\":\"龙湖\",\"level\":2,\"parentId\":440500}"),
                call("movies-1", "get_showing_movies", "{\"time\":\"2030-01-01T12:00:00\"}"),
                call("showtimes-1", "get_showtime_candidates", """
                        {"movieId":1,"cityId":440500,"districtId":440507,
                         "startFrom":"2030-01-01T12:00:00","startTo":"2030-01-01T18:00:00"}
                        """),
                AiMessage.from(answer("SHOWTIME_OPTIONS", "[{\"showtimeId\":1001,\"reason\":\"时间合适\"}]", "[]")));
        var response = service(model).chat("汕头龙湖下午有哪些场次？");
        assertThat(response.type()).isEqualTo(me.wly.movie_reservation.ai.protocol.AiResponseType.SHOWTIME_OPTIONS);
        verify(showtimes).searchCandidates(argThat(query -> query.cityId() == 440500 && query.districtId() == 440507));
        assertThat(model.requests).hasSize(6);
    }

    @Test
    void clarificationWorksWithoutTools() {
        var result = service(new ScriptedModel(AiMessage.from(answer("CLARIFICATION", "[]", "[\"CITY\"]"))))
                .chat("推荐一下");
        assertThat(result.blocks()).hasSize(2);
        assertThat(result.blocks().get(0)).isInstanceOf(AiBlock.Text.class);
        assertThat(((AiBlock.InputRequest) result.blocks().get(1)).data().missingFields())
                .containsExactly(me.wly.movie_reservation.ai.protocol.AiMissingField.CITY);
        verifyNoInteractions(movies, showtimes);
    }

    @Test
    void completedBlocksCanBeRestoredFromStoredJson() {
        var response = service(new ScriptedModel(AiMessage.from(answer("CLARIFICATION", "[]", "[\"CITY\"]"))))
                .chat("推荐一下");
        var mapper = JsonMapper.builder().build();

        AiResponseVO restored = mapper.readValue(mapper.writeValueAsString(response), AiResponseVO.class);

        assertThat(restored).isEqualTo(response);
        assertThat(restored.blocks().get(0)).isInstanceOf(AiBlock.Text.class);
        assertThat(restored.blocks().get(1)).isInstanceOf(AiBlock.InputRequest.class);
    }

    @Test
    void movieCardsUseActualToolObjects() {
        Movie movie = new Movie(1, "tt1", start.minusDays(1), null, "数据库电影",
                "真实简介", List.of("剧情"), "中文", "https://example.com/poster.jpg");
        when(movies.getShowingMoviesOn(start)).thenReturn(List.of(movie));
        AiMessage call = AiMessage.from(ToolExecutionRequest.builder().id("movies-1")
                .name("get_showing_movies").arguments("{\"time\":\"2030-01-01T12:00:00\"}").build());
        String reply = """
                {"type":"MOVIE_RECOMMENDATION","message":"推荐这部电影",
                 "movies":[{"movieId":1,"reason":"适合喜欢剧情片的观众"}],
                 "showtimes":[],"missingFields":[]}
                """;
        var result = service(new ScriptedModel(call, AiMessage.from(reply))).chat("推荐电影");
        var card = (AiBlock.MovieCard) result.blocks().get(1);
        assertThat(card.data().movie().title()).isEqualTo("数据库电影");
        assertThat(card.data().movie().posterImageUrl()).isEqualTo("https://example.com/poster.jpg");
    }

    @Test
    void providerFailureIsSanitizedAtHttpBoundary() throws Exception {
        var service = service(new ScriptedModel() {
            @Override public ChatResponse doChat(ChatRequest request) {
                throw new IllegalStateException("private provider response");
            }
        });
        var mvc = MockMvcBuilders.standaloneSetup(new AiController(service.application))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/ai").principal(() -> "alice").contentType("application/json").content(body("查询")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("AI response unavailable or invalid; please retry"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"showtimeId\":9999,\"reason\":\"不存在\"}]",
            "[{\"showtimeId\":1001,\"reason\":\"一\"},{\"showtimeId\":1001,\"reason\":\"二\"}]",
            "[]"
    })
    void rejectsUnknownDuplicateAndMissingRecommendations(String recommendations) {
        when(showtimes.searchCandidates(any())).thenReturn(new ShowtimeSearchResultDTO(List.of(candidate()), 0, 10, false));
        assertInvalid(service(new ScriptedModel(toolCall(),
                AiMessage.from(answer("SHOWTIME_OPTIONS", recommendations, "[]")))));
    }

    @Test
    void toolEvidenceCannotLeakToNextRequest() {
        when(showtimes.searchCandidates(any())).thenReturn(new ShowtimeSearchResultDTO(List.of(candidate()), 0, 10, false));
        String reply = answer("SHOWTIME_OPTIONS", "[{\"showtimeId\":1001,\"reason\":\"推荐\"}]", "[]");
        var service = service(new ScriptedModel(toolCall(), AiMessage.from(reply), AiMessage.from(reply)));
        assertThat(service.chat("查询").blocks())
                .filteredOn(block -> block instanceof AiBlock.ShowtimeCard).hasSize(1);
        assertInvalid(service);
    }

    @Test
    void nextTurnReceivesEarlierUserAndAssistantMessages() {
        var model = new ScriptedModel(AiMessage.from(answer("GENERAL", "[]", "[]")),
                AiMessage.from(answer("GENERAL", "[]", "[]")));
        var conversation = service(model);
        conversation.chat("我想在杭州看电影");
        conversation.chat("换成明天下午");
        assertThat(model.requests.getLast().messages()).anySatisfy(message ->
                assertThat(message.toString()).contains("我想在杭州看电影"));
        assertThat(model.requests.getLast().messages()).anySatisfy(message ->
                assertThat(message.toString()).contains("换成明天下午"));
    }

    @Test
    void noResultsRequiresAnActualEmptyQuery() {
        String reply = answer("NO_RESULTS", "[]", "[]");
        assertInvalid(service(new ScriptedModel(AiMessage.from(reply))));
        when(showtimes.searchCandidates(any())).thenReturn(new ShowtimeSearchResultDTO(List.of(), 0, 10, false));
        assertThat(service(new ScriptedModel(toolCall(), AiMessage.from(reply)))
                .chat("查询").blocks())
                .filteredOn(block -> block instanceof AiBlock.ShowtimeCard).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not JSON", "{}", "{\"type\":\"UNKNOWN\"}"})
    void malformedModelOutputBecomesControlledError(String reply) {
        assertInvalid(service(new ScriptedModel(AiMessage.from(reply))));
    }

    @Test
    void rejectsMissingClarificationFields() {
        assertInvalid(service(new ScriptedModel(AiMessage.from(answer("CLARIFICATION", "[]", "[]")))));
    }

    @Test
    void blankAndOversizedRequestsDoNotCallModel() throws Exception {
        ScriptedModel model = new ScriptedModel();
        var service = service(model);
        var mvc = MockMvcBuilders.standaloneSetup(new AiController(service.application))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/ai").principal(() -> "alice").contentType("application/json").content(body("  ")))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> service.chat("x".repeat(4001)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.resultCode).isEqualTo(ResultCode.BAD_REQUEST));
        assertThat(model.requests).isEmpty();
    }

    private TestConversation service(ScriptedModel model) {
        var properties = new AiConversationProperties();
        var store = mock(RedisAiConversationStore.class);
        var state = new java.util.concurrent.atomic.AtomicReference<>(AiConversationState.empty());
        when(store.begin(any(), any())).thenAnswer(call ->
                new RedisAiConversationStore.Lease(SESSION_ID, "alice", "token", "0", state.get()));
        when(store.commit(any(), any())).thenAnswer(call -> {
            state.set(call.getArgument(1));
            return Instant.now().plusSeconds(1800);
        });
        var application = new AiApplicationService(store, new AiAssistantFactory(model, tools, properties),
                new AiResponseAssembler(factory.getValidator()), properties, factory.getValidator(), Clock.systemUTC());
        return new TestConversation(application);
    }

    private void assertInvalid(TestConversation service) {
        assertThatThrownBy(() -> service.chat("查询"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.resultCode).isEqualTo(ResultCode.INTERNAL_SERVER_ERROR));
    }

    private ShowtimeCandidateDTO candidate() {
        return new ShowtimeCandidateDTO(1001L, 1, "tt1", "电影", 2, "影院", "真实地址",
                100, "城市", 101, "行政区", 3, "影厅", HallType.IMAX, start, start.plusHours(2),
                new BigDecimal("45.50"), 7);
    }

    private static String answer(String type, String showtimes, String missing) {
        return "{\"type\":\"" + type + "\",\"message\":\"查询说明\",\"movies\":[],\"showtimes\":"
                + showtimes + ",\"missingFields\":" + missing + "}";
    }

    private static AiMessage toolCall() {
        return AiMessage.from(ToolExecutionRequest.builder().id("query-1").name("get_showtime_candidates")
                .arguments("""
                        {"movieId":1,"cityId":100,"startFrom":"2030-01-01T12:00:00",
                         "startTo":"2030-01-01T18:00:00"}
                        """).build());
    }

    private static AiMessage call(String id, String name, String arguments) {
        return AiMessage.from(ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build());
    }

    private AreaSearchResultDTO resolvedCity() {
        return new AreaSearchResultDTO("汕头", AreaSearchResultDTO.Status.MATCHED,
                List.of(new AreaCandidateDTO(440500, "汕头市", 1, 440000, "广东省")), false);
    }

    private AreaSearchResultDTO resolvedDistrict() {
        return new AreaSearchResultDTO("龙湖", AreaSearchResultDTO.Status.MATCHED,
                List.of(new AreaCandidateDTO(440507, "龙湖区", 2, 440500, "汕头市")), false);
    }

    /** Real AI Service parsing/tool loop, deterministic responses, no network or API key. */
    private static class ScriptedModel implements ChatModel {
        private final Queue<AiMessage> replies;
        private final List<ChatRequest> requests = new ArrayList<>();
        ScriptedModel(AiMessage... replies) { this.replies = new ArrayDeque<>(List.of(replies)); }
        @Override public Set<Capability> supportedCapabilities() {
            return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
        }
        @Override public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(replies.remove()).build();
        }
    }
}
