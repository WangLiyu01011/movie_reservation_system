package me.wly.movie_reservation.model.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeCardVO(
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal price,
        String theaterName,
        String hallName,
        String movieTitle
) {}
