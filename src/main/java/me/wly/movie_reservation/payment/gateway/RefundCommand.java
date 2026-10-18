package me.wly.movie_reservation.payment.gateway;

import java.math.BigDecimal;

/** refundNo is the provider idempotency key, reused after ambiguous timeouts. */
public record RefundCommand(String refundNo, String paymentNo, String providerTradeNo,
                            BigDecimal amount, String reason) { }
