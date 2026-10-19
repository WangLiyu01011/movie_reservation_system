package me.wly.movie_reservation.ai;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.*;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.Set;
import static me.wly.movie_reservation.ai.AiFailureCode.*;

/** Measures every individual model round, not the entire tool-calling loop as one network request. */
final class AiObservedChatModel implements ChatModel {
    private final ChatModel delegate;
    private final AiDiagnostics diagnostics;
    AiObservedChatModel(ChatModel delegate, AiDiagnostics diagnostics) {
        this.delegate = delegate;
        this.diagnostics = diagnostics;
    }
    @Override public ChatResponse chat(ChatRequest request, ChatRequestOptions options) {
        long started = diagnostics.modelStarted();
        try {
            var response = delegate.chat(request, options);
            if (response.finishReason() == FinishReason.LENGTH) throw new AiDiagnosticException(MODEL_OUTPUT_TRUNCATED);
            diagnostics.success(Stage.MODEL, "-", started);
            if (!response.aiMessage().hasToolExecutionRequests()) diagnostics.parsingStarted();
            diagnostics.checkBudget(false);
            return response;
        } catch (RuntimeException error) {
            var code = AiDiagnostics.classify(error, Stage.MODEL);
            diagnostics.failure(code, error, "-", started);
            throw error instanceof AiDiagnosticException diagnostic ? diagnostic : new AiDiagnosticException(code, error);
        }
    }
    @Override public ChatRequestParameters defaultRequestParameters() { return delegate.defaultRequestParameters(); }
    @Override public Set<Capability> supportedCapabilities() { return delegate.supportedCapabilities(); }
    @Override public ModelProvider provider() { return delegate.provider(); }
}
