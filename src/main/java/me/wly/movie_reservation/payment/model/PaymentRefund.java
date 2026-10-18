package me.wly.movie_reservation.payment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One full refund per payment, including automatic refunds of excess/late charges. */
@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "payment_refund", uniqueConstraints = {
        @UniqueConstraint(name = "uk_refund_no", columnNames = "refund_no"),
        @UniqueConstraint(name = "uk_refund_payment", columnNames = "payment_id")})
public class PaymentRefund {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "refund_no", nullable = false, length = 64) private String refundNo;
    @ManyToOne @JoinColumn(name = "payment_id", nullable = false) private PaymentTransaction payment;
    @Column(name = "request_id", nullable = false, length = 64) private String requestId;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 255) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private RefundStatus status;
    @Column(name = "release_sold_seats", nullable = false) private boolean releaseSoldSeats;
    @Column(name = "provider_refund_no", length = 128) private String providerRefundNo;
    @Column(name = "failure_message", length = 512) private String failureMessage;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "refunded_at") private LocalDateTime refundedAt;
}
