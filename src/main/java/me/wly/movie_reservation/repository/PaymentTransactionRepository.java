package me.wly.movie_reservation.repository;

import me.wly.movie_reservation.model.entity.PaymentTransaction;
import me.wly.movie_reservation.model.enum_class.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<PaymentTransaction> findAllByOrder_IdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);
}
