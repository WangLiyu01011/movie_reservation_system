package me.wly.movie_reservation.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record AiRequestDTO(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String sessionId,
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_-]+") String requestId,
        @NotBlank @Size(max = 4000) String userMessage
) {

}
