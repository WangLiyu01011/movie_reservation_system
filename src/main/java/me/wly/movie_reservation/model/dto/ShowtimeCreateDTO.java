package me.wly.movie_reservation.model.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeCreateDTO(
        @NotNull @Future LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull Integer theaterId,
        @NotNull Integer hallId,
        @NotBlank String imdbId,
        @NotNull @Positive BigDecimal price
)
{}
