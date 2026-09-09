package me.wly.movie_reservation.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderCreateDTO(
        @NotNull Long showtimeId,
        @NotEmpty List<Long> seatIds,
        @NotBlank String requestId
) { }
