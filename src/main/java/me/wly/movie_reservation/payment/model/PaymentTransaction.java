package me.wly.movie_reservation.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.wly.movie_reservation.common.util.UuidCodeGenerator;
import me.wly.movie_reservation.order.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "payment_transaction",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_transaction_no", columnNames = "payment_no"),
                @UniqueConstraint(name = "uk_payment_transaction_order_request", columnNames = {"order_id", "request_id"}),
                @UniqueConstraint(name = "uk_payment_transaction_provider_trade", columnNames = "provider_trade_no")
        },
        indexes = @Index(name = "idx_payment_transaction_order_status_id", columnList = "order_id,status,id")
)
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_no", nullable = false, length = 64)
    private String paymentNo;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Front-end generated idempotency key */
    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    /** Payment provider identifier */
    @Column(nullable = false, length = 32)
    private String channel;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "provider_trade_no", length = 128)
    private String providerTradeNo;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 512)
    private String failureMessage;

    @Lob
    @Column(name = "callback_payload", columnDefinition = "LONGTEXT")
    private String callbackPayload;

    private LocalDateTime paidAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime expiresAt;

    /** Provider-issued checkout URL. Do not write its tokenized value to application logs. */
    @Column(name = "pay_url", length = 2048)
    private String payUrl;

    @PrePersist
    public void prePersist() {
        if (paymentNo == null) {
            paymentNo = UuidCodeGenerator.generateCode("pay_");
        }
        if (status == null) {
            status = PaymentStatus.CREATED;
        }
    }

}
