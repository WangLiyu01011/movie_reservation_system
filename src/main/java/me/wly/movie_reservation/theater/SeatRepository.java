package me.wly.movie_reservation.theater;

import me.wly.movie_reservation.theater.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findAllByHall_IdOrderByXAscYAsc(Integer hallId);
}
