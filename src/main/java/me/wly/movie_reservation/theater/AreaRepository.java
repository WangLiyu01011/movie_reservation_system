package me.wly.movie_reservation.theater;

import me.wly.movie_reservation.theater.model.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AreaRepository extends JpaRepository<Area, Integer> {
}
