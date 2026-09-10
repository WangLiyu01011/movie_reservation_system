package me.wly.movie_reservation.payment.vo;

import me.wly.movie_reservation.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRequestVO(
        String paymentNo,
        String orderCode,
        String channel,
        BigDecimal amount,
        PaymentStatus status,
        LocalDateTime expiresAt,
        String payUrl
) {
}
