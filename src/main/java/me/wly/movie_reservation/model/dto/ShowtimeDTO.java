package me.wly.movie_reservation.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeDTO (
        LocalDateTime startTime,
        LocalDateTime endTime,
        String hallName,
        String movieTitle,
        BigDecimal price
){ }
