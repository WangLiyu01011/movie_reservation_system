package me.wly.movie_reservation.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderGenerateDTO(
        @NotNull Long showtimeId,
        @NotEmpty List<Long> seatIds,
        @NotBlank String requestId
) { }
