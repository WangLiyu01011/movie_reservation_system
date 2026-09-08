package me.wly.movie_reservation.repository;

import me.wly.movie_reservation.model.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findAllByHall_IdOrderByXAscYAsc(Integer hallId);
}
