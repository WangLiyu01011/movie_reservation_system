package me.wly.movie_reservation.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;

/** Provider-authoritative response, never populated from a customer's request. */
public record PaymentQueryResult(String paymentNo, String providerTradeNo, BigDecimal amount,
                                 GatewayPaymentStatus status, Instant paidAt,
                                 String failureCode, String failureMessage) { }
