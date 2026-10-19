package me.wly.movie_reservation.ai;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.ai.dto.AiRequestDTO;
import me.wly.movie_reservation.ai.vo.*;
import me.wly.movie_reservation.common.exception.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.ArrayList;

@Service @RequiredArgsConstructor
public class AiApplicationService {
    private final RedisAiConversationStore conversations;
    private final AiAssistantFactory assistants;
    private final AiResponseAssembler assembler;
    private final AiConversationProperties properties;
    private final Validator validator;
    private final Clock businessClock;

    public AiChatReplyVO chat(AiRequestDTO request, String owner) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Invalid AI session, request ID or message");
        }
        requireOwner(owner);
        try (var diagnostics = AiDiagnostics.open(request, properties)) {
            try { return chatInSession(request, owner, diagnostics); }
            catch (RuntimeException error) { diagnostics.requestFailed(error); throw error; }
        }
    }

    private AiChatReplyVO chatInSession(AiRequestDTO request, String owner, AiDiagnostics diagnostics) {
        var lease = diagnostics.measure(AiFailureCode.Stage.STORE, () -> conversations.begin(request.sessionId(), owner));
        try {
            for (AiTurnVO turn : lease.state().turns()) {
                if (turn.requestId().equals(request.requestId())) {
                    if (!turn.userMessage().equals(request.userMessage())) throw new BusinessException(ResultCode.AI_REQUEST_CONFLICT);
                    return new AiChatReplyVO(lease.sessionId(), request.requestId(), conversations.expiresAt(), turn.response());
                }
            }
            if (lease.state().turns().size() >= properties.getMaxTurns()) throw new BusinessException(ResultCode.AI_SESSION_LIMIT);
            AiResponseVO response;
            String messagesJson;
            diagnostics.beginGeneration();
            try {
                var invocation = diagnostics.measure(AiFailureCode.Stage.SETUP,
                        () -> assistants.start(lease.state().messagesJson()));
                var answer = invocation.assistant().chat(request.userMessage());
                diagnostics.parsingSucceeded();
                diagnostics.checkBudget(false);
                response = diagnostics.measure(AiFailureCode.Stage.VALIDATION, () -> assembler.assemble(answer));
                messagesJson = diagnostics.measure(AiFailureCode.Stage.MEMORY, invocation::messagesJson);
                diagnostics.checkBudget(false);
            } catch (RuntimeException exception) {
                throw diagnostics.generationFailed(exception);
            }
            var turns = new ArrayList<>(lease.state().turns());
            turns.add(new AiTurnVO(request.requestId(), request.userMessage(), response, businessClock.instant()));
            var expiresAt = diagnostics.measure(AiFailureCode.Stage.STORE,
                    () -> conversations.commit(lease, new AiConversationState(messagesJson, turns)));
            return new AiChatReplyVO(lease.sessionId(), request.requestId(), expiresAt, response);
        } finally { conversations.release(lease); }
    }

    public AiSessionVO createSession(String owner) { requireOwner(owner); return conversations.create(owner); }
    public AiSessionVO getSession(String id, String owner) { requireOwner(owner); return conversations.read(id, owner); }
    public void deleteSession(String id, String owner) { requireOwner(owner); conversations.delete(id, owner); }
    private void requireOwner(String owner) {
        if (owner == null || owner.isBlank()) throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}
