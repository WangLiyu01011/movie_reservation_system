package me.wly.movie_reservation.payment;

import me.wly.movie_reservation.payment.model.PaymentCallbackEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentCallbackEventRepository extends JpaRepository<PaymentCallbackEvent, Long> {
    Optional<PaymentCallbackEvent> findByChannelAndEventId(String channel, String eventId);
}
