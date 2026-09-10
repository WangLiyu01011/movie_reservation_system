package me.wly.movie_reservation.order;

import me.wly.movie_reservation.order.model.OrderSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderSeatRepository extends JpaRepository<OrderSeat, Long> {

}
