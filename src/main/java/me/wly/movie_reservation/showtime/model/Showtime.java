package me.wly.movie_reservation.showtime.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.theater.model.Hall;
import me.wly.movie_reservation.theater.model.Theater;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "showtime",
        indexes = {
                @Index(name = "idx_showtime_theater_movie_start", columnList = "theater_id,movie_id,start_time"),
                @Index(name = "idx_showtime_hall_interval", columnList = "hall_id,start_time,end_time")
        }
)
public class Showtime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;
    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal price;
    @ManyToOne
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;
    @Column(name = "theater_name", length = 100, nullable = false)
    private String theaterName;
    @ManyToOne
    @JoinColumn(name = "hall_id", nullable = false)
    private Hall hall;
    @Column(name = "hall_name", length = 40, nullable = false)
    private String hallName;
    @ManyToOne
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;
    @Column(name = "movie_title", length = 50, nullable = false)
    private String movieTitle;

}
