package me.wly.movie_reservation.order;

import jakarta.persistence.LockModeType;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderStatus;
import me.wly.movie_reservation.user.model.User;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /** Fetches only a bounded batch of candidates; each candidate is locked separately before expiry. */
    @Query("""
            select orderEntity.id from Order orderEntity
            where orderEntity.status = :status
              and (orderEntity.expiresAt is null or orderEntity.expiresAt <= :now)
            order by orderEntity.expiresAt asc, orderEntity.id asc
            """)
    List<Long> findExpiredOrderIds(
            @Param("status") OrderStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select orderEntity from Order orderEntity
            where orderEntity.id = :orderId
            """)
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);
}
