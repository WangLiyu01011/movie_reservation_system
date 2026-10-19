package me.wly.movie_reservation.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class AiModelConfig {
    @Bean("movieChatModel")
    public ChatModel movieChatModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName,
            AiConversationProperties properties) {

        var builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(properties.getModelTimeout())
                .maxRetries(0)
                // JSON Mode constrains syntax; the prompt and assembler validate the response fields.
                .responseFormat("json_object")
                .logRequests(false).logResponses(false);
        boolean kimi = modelName.startsWith("kimi-");
        boolean disabled = properties.getThinkingMode() == AiConversationProperties.ThinkingMode.DISABLED;
        if (!kimi || !disabled) builder.temperature(1.0);
        if (kimi) {
            // Preserve reasoning within tool loops when using the provider's default/enabled thinking mode.
            builder.returnThinking(!disabled).sendThinking(!disabled);
            if (properties.getThinkingMode() != AiConversationProperties.ThinkingMode.DEFAULT) {
                builder.customParameters(java.util.Map.of("thinking", java.util.Map.of("type", disabled ? "disabled" : "enabled")));
            }
        } else if (properties.getThinkingMode() != AiConversationProperties.ThinkingMode.DEFAULT) {
            throw new IllegalArgumentException("Explicit thinking-mode is only configured for Kimi models");
        }
        if (properties.getMaxOutputTokens() != null) builder.maxTokens(properties.getMaxOutputTokens());
        return builder.build();
    }

}
