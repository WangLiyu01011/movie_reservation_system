package me.wly.movie_reservation.payment;

import me.wly.movie_reservation.payment.model.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    Optional<PaymentRefund> findByPayment_Id(Long paymentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refund from PaymentRefund refund where refund.payment.id = :paymentId")
    Optional<PaymentRefund> findForUpdate(@Param("paymentId") Long paymentId);
}
