package me.wly.movie_reservation.ai;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Per-invocation memory: failed generations cannot mutate committed Redis history. */
@Component
public class AiAssistantFactory {
    private final ChatModel model;
    private final AiTools tools;
    private final AiConversationProperties properties;
    public AiAssistantFactory(@Qualifier("movieChatModel") ChatModel model, AiTools tools, AiConversationProperties properties) {
        this.model = model; this.tools = tools; this.properties = properties;
    }
    public Invocation start(String historyJson) {
        var diagnostics = AiDiagnostics.currentOrDetached(properties);
        diagnostics.beginGeneration();
        // LangChain4j 1.20 assembles the initial request from memory, then appends the current user message.
        // Reserve one slot so maxMessages bounds the actual model request as well as persisted memory.
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(properties.getMaxMessages() - 1)
                .alwaysKeepSystemMessageFirst(true).build();
        try { ChatMessageDeserializer.messagesFromJson(historyJson).forEach(memory::add); }
        catch (RuntimeException error) { throw new AiDiagnosticException(AiFailureCode.HISTORY_INVALID, error); }
        MovieAssistant assistant = AiServices.builder(MovieAssistant.class)
                .chatModel(new AiObservedChatModel(model, diagnostics)).tools(tools)
                .beforeToolExecution(event -> diagnostics.toolStarted(event.request().name()))
                .afterToolExecution(event -> {
                    if (!event.hasFailed()) diagnostics.toolSucceeded(event.request().name());
                })
                .toolArgumentsErrorHandler((error, context) -> {
                    throw diagnostics.toolFailed(AiFailureCode.TOOL_ARGUMENTS_INVALID,
                            context.toolExecutionRequest().name(), error);
                })
                .toolExecutionErrorHandler((error, context) -> {
                    throw diagnostics.toolFailed(AiFailureCode.TOOL_EXECUTION_FAILED,
                            context.toolExecutionRequest().name(), context.rawError() == null ? error : context.rawError());
                })
                .hallucinatedToolNameStrategy(request -> {
                    throw diagnostics.toolFailed(AiFailureCode.UNKNOWN_TOOL, request.name(), null);
                })
                .chatMemory(memory).maxToolCallingRoundTrips(properties.getMaxToolRoundTrips()).build();
        return new Invocation(assistant, memory);
    }
    public record Invocation(MovieAssistant assistant, ChatMemory memory) {
        public String messagesJson() {
            return ChatMessageSerializer.messagesToJson(memory.messages().stream()
                    .filter(message -> !(message instanceof SystemMessage)).toList());
        }
    }
}
