package me.wly.movie_reservation.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentRequestDTO(
        @NotBlank @Size(max = 64) String orderCode,
        @NotBlank @Size(max = 32) String channel,
        @NotBlank @Size(max = 64) String requestId
) {
}
