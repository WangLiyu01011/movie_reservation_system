package me.wly.movie_reservation.theater.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import me.wly.movie_reservation.theater.model.SeatType;

public record SeatCellCreateDTO(
        @NotNull @Min(1) Integer row,
        @NotNull @Min(1) Integer column,
        @NotNull SeatType seatType
) { }
