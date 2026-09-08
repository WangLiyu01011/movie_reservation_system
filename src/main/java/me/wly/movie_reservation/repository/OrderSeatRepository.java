package me.wly.movie_reservation.repository;

import me.wly.movie_reservation.model.entity.OrderSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderSeatRepository extends JpaRepository<OrderSeat, Long> {
}
