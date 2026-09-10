package me.wly.movie_reservation.payment;

import me.wly.movie_reservation.payment.model.PaymentStatus;
import me.wly.movie_reservation.payment.model.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByPaymentNo(String paymentNo);

    Optional<PaymentTransaction> findByOrder_IdAndRequestId(Long orderId, String requestId);

    Optional<PaymentTransaction> findByProviderTradeNo(String providerTradeNo);

    Optional<PaymentTransaction> findFirstByOrder_IdAndStatusInOrderByIdDesc(
            Long orderId,
            Collection<PaymentStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PaymentTransaction> findAllByOrder_IdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);
}
