package me.wly.movie_reservation.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.wly.movie_reservation.payment.model.PaymentCallbackStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCallbackDTO(
        @NotBlank
        String eventId,

        @NotBlank
        String paymentNo,

        @NotBlank
        String providerTradeNo,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount,

        @NotNull
        PaymentCallbackStatus status,

        Instant paidAt,

        @Size(max = 64)
        String failureCode,

        @Size(max = 512)
        String failureMessage
) {
}
