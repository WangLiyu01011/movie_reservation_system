package me.wly.movie_reservation.showtime;

import me.wly.movie_reservation.showtime.model.Showtime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface
ShowtimeRepository extends JpaRepository<Showtime, Long> {
    List<Showtime> findShowtimeByTheater_Id(Integer theaterId);
    List<Showtime> findShowtimeByTheater_IdAndMovie_ImdbId(Integer theaterId, String imdbId);
    // 当前读，确保读取最新的状态
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Showtime> findFirstByHall_IdAndStartTimeLessThanAndEndTimeGreaterThan(
            Integer hallId,
            LocalDateTime endTime,
            LocalDateTime startTime
    );
}
