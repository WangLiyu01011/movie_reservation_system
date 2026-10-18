package me.wly.movie_reservation.payment.vo;

import me.wly.movie_reservation.payment.model.*;
import java.time.LocalDateTime;

public record PaymentTaskVO(Long id, String paymentNo, PaymentTaskType type, PaymentTaskStatus status,
                            int attempts, LocalDateTime nextAttemptAt, String lastError) { }
