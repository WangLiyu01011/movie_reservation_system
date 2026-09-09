package me.wly.movie_reservation.repository;

import me.wly.movie_reservation.model.entity.ShowtimeSeat;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;

@Repository
public interface ShowtimeSeatRepository extends JpaRepository<ShowtimeSeat, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select showtimeSeat from ShowtimeSeat showtimeSeat
            where showtimeSeat.showtime.id = :showtimeId
              and showtimeSeat.seat.id in :seatIds
            order by showtimeSeat.id
            """)
    List<ShowtimeSeat> findAllForUpdate(
            @Param("showtimeId") Long showtimeId,
            @Param("seatIds") Collection<Long> seatIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select showtimeSeat from ShowtimeSeat showtimeSeat
            where showtimeSeat.order.id = :orderId
              and showtimeSeat.status = me.wly.movie_reservation.model.enum_class.SeatStatus.LOCKED
            """)
    List<ShowtimeSeat> findLockedByOrderIdForUpdate(@Param("orderId") Long orderId);
}
