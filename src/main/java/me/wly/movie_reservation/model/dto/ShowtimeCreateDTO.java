package me.wly.movie_reservation.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeCreateDTO(
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer theaterId,
        Integer hallId,
        String imdbId,
        BigDecimal price
)
{}
