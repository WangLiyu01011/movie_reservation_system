package me.wly.movie_reservation.model.vo;

import me.wly.movie_reservation.model.enum_class.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderPayVO(
        String paymentNo,
        String orderCode,
        String channel,
        BigDecimal amount,
        PaymentStatus status,
        LocalDateTime expiresAt
) {
}
