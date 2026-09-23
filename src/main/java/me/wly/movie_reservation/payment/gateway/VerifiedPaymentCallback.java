package me.wly.movie_reservation.payment.gateway;

import me.wly.movie_reservation.payment.model.PaymentCallbackStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** 经过提供商signature确认后的callback data */
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
