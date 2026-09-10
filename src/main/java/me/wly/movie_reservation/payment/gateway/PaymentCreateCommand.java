package me.wly.movie_reservation.payment.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Provider-neutral parameters required to create a payment order.
 */
public record PaymentCreateCommand(
        String paymentNo,
        String orderCode,
        BigDecimal amount,
        String subject,
        LocalDateTime expiresAt,
        String callbackUrl
) {
    public PaymentCreateCommand {
        Objects.requireNonNull(paymentNo, "paymentNo must not be null");
        Objects.requireNonNull(orderCode, "orderCode must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(callbackUrl, "callbackUrl must not be null");

        if (paymentNo.isBlank()) {
            throw new IllegalArgumentException("paymentNo must not be blank");
        }
        if (orderCode.isBlank()) {
            throw new IllegalArgumentException("orderCode must not be blank");
        }
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (callbackUrl.isBlank()) {
            throw new IllegalArgumentException("callbackUrl must not be blank");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }
}
