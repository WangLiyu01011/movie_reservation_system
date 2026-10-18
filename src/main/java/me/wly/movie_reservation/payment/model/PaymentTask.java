package me.wly.movie_reservation.payment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "payment_task", uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_task_type", columnNames = {"payment_id", "type"}), indexes = {
        @Index(name = "idx_payment_task_due", columnList = "status,next_attempt_at,id"),
        @Index(name = "idx_payment_task_lease", columnList = "status,lease_until,id")})
public class PaymentTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name = "payment_id", nullable = false) private PaymentTransaction payment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PaymentTaskType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PaymentTaskStatus status;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private LocalDateTime nextAttemptAt;
    @Column(name = "lease_until") private LocalDateTime leaseUntil;
    @Column(name = "lease_token", length = 64) private String leaseToken;
    @Column(name = "last_error", length = 512) private String lastError;
}
