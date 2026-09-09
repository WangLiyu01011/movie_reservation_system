package me.wly.movie_reservation.repository;

import jakarta.persistence.LockModeType;
import me.wly.movie_reservation.model.entity.Order;
import me.wly.movie_reservation.model.entity.User;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> getOrdersByUser(User user);
    Optional<Order> findByUser_IdAndRequestId(Long userId, String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select orderEntity from Order orderEntity
            where orderEntity.code = :orderCode
              and orderEntity.user.id = :userId
            """)
    Optional<Order> findByCodeAndUserIdForUpdate(
            @Param("orderCode") String orderCode,
            @Param("userId") Long userId
    );
}
