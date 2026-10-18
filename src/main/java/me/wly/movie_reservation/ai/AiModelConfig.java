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

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(1.0)
                .timeout(properties.getModelTimeout())
                .maxRetries(0)
                // JSON Mode constrains syntax; the prompt and assembler validate the response fields.
                .responseFormat("json_object")
                .build();
    }

}
