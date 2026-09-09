package me.wly.movie_reservation.model.entity;

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
import me.wly.movie_reservation.common.utils.UuidCodeGenerator;
import me.wly.movie_reservation.model.enum_class.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_no", nullable = false, length = 64)
    private String paymentNo;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Front-end generated idempotency key for initiating a payment. */
    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    /** Payment provider identifier, such as ALIPAY or WECHAT. */
    @Column(nullable = false, length = 32)
    private String channel;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "provider_trade_no", length = 128)
    private String providerTradeNo;

    @Lob
    @Column(name = "callback_payload", columnDefinition = "LONGTEXT")
    private String callbackPayload;

    private LocalDateTime paidAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

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
