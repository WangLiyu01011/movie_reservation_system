package me.wly.movie_reservation.payment.vo;

import me.wly.movie_reservation.payment.model.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundVO(String refundNo, String paymentNo, BigDecimal amount, RefundStatus status,
                       String failureMessage, LocalDateTime refundedAt) { }
