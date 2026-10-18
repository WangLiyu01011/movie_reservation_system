package me.wly.movie_reservation.ai;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import me.wly.movie_reservation.ai.dto.AiRequestDTO;
import me.wly.movie_reservation.ai.protocol.AiResponseType;
import me.wly.movie_reservation.ai.vo.*;
import me.wly.movie_reservation.common.exception.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiConversationServiceTest {
    private static final String ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static ValidatorFactory validators;
    private final RedisAiConversationStore store = mock(RedisAiConversationStore.class);
    private final AiAssistantFactory assistants = mock(AiAssistantFactory.class);
    private final AiConversationProperties properties = new AiConversationProperties();
    private final Clock clock = Clock.systemUTC();
    private AiApplicationService service;
    @BeforeAll static void open() { validators = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { validators.close(); }
    @BeforeEach void setUp() {
        service = new AiApplicationService(store, assistants, new AiResponseAssembler(validators.getValidator()),
                properties, validators.getValidator(), clock);
    }
    private AiRequestDTO request(String requestId, String message) { return new AiRequestDTO(ID, requestId, message); }
    private AiResponseVO response() { return new AiResponseVO(2, AiResponseType.GENERAL,
            List.of(new AiBlock.Text("text-0", new AiBlock.TextData("回答"))), List.of()); }
    private void state(List<AiTurnVO> turns) {
        when(store.begin(ID, "alice")).thenReturn(new RedisAiConversationStore.Lease(ID, "alice", "token", "0", new AiConversationState("[]", turns)));
    }
    @Test void replay_returnsCommittedResponseWithoutCallingModelAgain() {
        var answer = response(); state(List.of(new AiTurnVO("r1", "问题", answer, clock.instant())));
        assertThat(service.chat(request("r1", "问题"), "alice").response()).isSameAs(answer);
        verifyNoInteractions(assistants); verify(store, never()).commit(any(), any()); verify(store).release(any());
    }
    @Test void requestIdWithDifferentContent_returnsConflict() {
        state(List.of(new AiTurnVO("r1", "原问题", response(), clock.instant())));
        assertCode(ResultCode.AI_REQUEST_CONFLICT, () -> service.chat(request("r1", "新问题"), "alice"));
        verifyNoInteractions(assistants); verify(store).release(any());
    }
    @Test void limit_blocksNewTurnsButStillAllowsReplay() {
        properties.setMaxTurns(1); state(List.of(new AiTurnVO("r1", "问题", response(), clock.instant())));
        assertCode(ResultCode.AI_SESSION_LIMIT, () -> service.chat(request("r2", "新问题"), "alice"));
        assertThat(service.chat(request("r1", "问题"), "alice").response()).isNotNull(); verifyNoInteractions(assistants);
    }
    @Test void invalidRequest_doesNotAccessRedisOrModel() {
        assertCode(ResultCode.BAD_REQUEST, () -> service.chat(request("r1", " "), "alice"));
        assertCode(ResultCode.BAD_REQUEST, () -> service.chat(request("r1", "x".repeat(4001)), "alice"));
        assertCode(ResultCode.BAD_REQUEST, () -> service.chat(new AiRequestDTO("bad", "r1", "问题"), "alice"));
        verifyNoInteractions(store, assistants);
    }
    @Test void unauthenticatedRequest_isRejected() {
        assertCode(ResultCode.UNAUTHORIZED, () -> service.chat(request("r1", "问题"), null));
        verifyNoInteractions(store, assistants);
    }
    @Test void busySession_doesNotStartAnotherGeneration() {
        when(store.begin(ID, "alice")).thenThrow(new BusinessException(ResultCode.AI_SESSION_BUSY));
        assertCode(ResultCode.AI_SESSION_BUSY, () -> service.chat(request("r1", "问题"), "alice")); verifyNoInteractions(assistants);
    }
    @Test void missingOrForeignSession_isNotSilentlyRecreated() {
        when(store.begin(ID, "bob")).thenThrow(new BusinessException(ResultCode.AI_SESSION_NOT_FOUND));
        assertCode(ResultCode.AI_SESSION_NOT_FOUND, () -> service.chat(request("r1", "问题"), "bob")); verifyNoInteractions(assistants);
    }
    @Test void generationFailure_releasesLeaseWithoutSavingPartialMessages() {
        state(List.of()); when(assistants.start("[]")).thenThrow(new IllegalStateException("private provider details"));
        assertThatThrownBy(() -> service.chat(request("r1", "问题"), "alice")).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getMessage()).doesNotContain("private provider details"));
        verify(store, never()).commit(any(), any()); verify(store).release(any());
    }
    private void assertCode(ResultCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.resultCode).isEqualTo(code));
    }
}
