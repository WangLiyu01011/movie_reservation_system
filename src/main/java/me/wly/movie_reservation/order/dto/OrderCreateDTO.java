package me.wly.movie_reservation.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreateDTO(
        @NotNull @Positive Long showtimeId,
        @NotEmpty List<@NotNull @Positive Long> seatIds,
        @NotBlank @Size(max = 128) String requestId
) { }
