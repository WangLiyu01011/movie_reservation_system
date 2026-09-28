package me.wly.movie_reservation.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "payment_callback_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_callback_event_channel_event",
                columnNames = {"channel", "event_id"}
        ),
        indexes = @Index(
                name = "idx_payment_callback_event_payment_no",
                columnList = "payment_no"
        )
)
public class PaymentCallbackEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "payment_no", nullable = false, length = 64)
    private String paymentNo;

    @Column(name = "provider_trade_no", nullable = false, length = 128)
    private String providerTradeNo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "callback_status", nullable = false, length = 16)
    private PaymentCallbackStatus callbackStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_status", nullable = false, length = 32)
    private PaymentCallbackProcessStatus processStatus;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 512)
    private String failureMessage;

    @Lob
    @Column(name = "raw_payload", nullable = false, columnDefinition = "LONGTEXT")
    private String rawPayload;

    @Column(name = "processing_message", length = 512)
    private String processingMessage;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "last_received_at", nullable = false)
    private LocalDateTime lastReceivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() {
        if (processStatus == null) {
            processStatus = PaymentCallbackProcessStatus.RECEIVED;
        }
        java.util.Objects.requireNonNull(receivedAt, "receivedAt must be set using the business clock");
        if (lastReceivedAt == null) {
            lastReceivedAt = receivedAt;
        }
    }
}
