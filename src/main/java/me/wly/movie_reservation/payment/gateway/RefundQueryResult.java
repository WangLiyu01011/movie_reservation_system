package me.wly.movie_reservation.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundQueryResult(String refundNo, String paymentNo, String providerRefundNo,
                                BigDecimal amount, GatewayRefundStatus status, Instant refundedAt,
                                String failureMessage) { }
