package me.wly.movie_reservation.repository;

import me.wly.movie_reservation.model.entity.Order;
import me.wly.movie_reservation.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> getOrdersByUser(User user);
}
