package me.wly.movie_reservation.repository;

import jakarta.validation.constraints.NotNull;
import me.wly.movie_reservation.model.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface
ShowtimeRepository extends JpaRepository<Showtime, Long> {
    List<Showtime> findShowtimeByTheater_Id(Integer theaterId);
    List<Showtime> findShowtimeByTheater_IdAndMovie_ImdbId(Integer theaterId, String imdbId);
}
