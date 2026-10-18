package me.wly.movie_reservation.payment;

import jakarta.persistence.LockModeType;
import me.wly.movie_reservation.payment.model.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentTaskRepository extends JpaRepository<PaymentTask, Long> {
    Optional<PaymentTask> findByPayment_PaymentNoAndType(String paymentNo, PaymentTaskType type);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTask> findByPayment_IdAndType(Long paymentId, PaymentTaskType type);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from PaymentTask task where task.id = :id")
    Optional<PaymentTask> findByIdForUpdate(@Param("id") Long id);
    @Query("""
            select task.id from PaymentTask task
            where (task.status = me.wly.movie_reservation.payment.model.PaymentTaskStatus.PENDING
                   and task.nextAttemptAt <= :now)
               or (task.status = me.wly.movie_reservation.payment.model.PaymentTaskStatus.RUNNING
                   and task.leaseUntil <= :now)
            order by task.nextAttemptAt, task.id
            """)
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);
    List<PaymentTask> findByStatusOrderById(PaymentTaskStatus status, Pageable pageable);
}
