package me.wly.movie_reservation.payment.gateway;

import java.time.LocalDateTime;

/**
 * Provider-neutral result returned after the payment platform accepts a payment order.
 */
public record PaymentCreateResult(
        String providerTradeNo,
        String payUrl,
        LocalDateTime expiresAt
) {
}
