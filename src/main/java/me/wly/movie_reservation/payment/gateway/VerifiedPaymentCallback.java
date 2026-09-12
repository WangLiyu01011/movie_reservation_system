package me.wly.movie_reservation.payment.gateway;

import me.wly.movie_reservation.payment.model.PaymentCallbackStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Callback data that has already passed provider signature verification. */
public record VerifiedPaymentCallback(
        String eventId,
        String channel,
        String paymentNo,
        String providerTradeNo,
        BigDecimal amount,
        PaymentCallbackStatus status,
        Instant paidAt,
        String failureCode,
        String failureMessage,
        String rawPayload
) {
}
