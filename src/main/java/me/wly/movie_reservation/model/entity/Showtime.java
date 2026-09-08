package me.wly.movie_reservation.model.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Showtime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @Column(precision = 5, scale = 2)
    private BigDecimal price;
    @ManyToOne
    @JoinColumn(name = "theater_id")
    private Theater theater;
    private String theaterName;
    @ManyToOne
    @JoinColumn(name = "hall_id")
    private Hall hall;
    private String hallName;
    @ManyToOne
    @JoinColumn(name = "movie_id")
    private Movie movie;
    private String movieTitle;

}
