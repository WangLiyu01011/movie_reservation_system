package me.wly.movie_reservation.showtime.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeDTO (
        Long id,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String theaterName,
        String hallName,
        String movieTitle,
        BigDecimal price
){ }
