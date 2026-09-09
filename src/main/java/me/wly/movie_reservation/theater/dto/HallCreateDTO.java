package me.wly.movie_reservation.theater.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.wly.movie_reservation.theater.model.HallType;

import java.util.List;

public record HallCreateDTO(
        @NotNull Integer theaterId,
        @NotBlank @Size(max = 40) String name,
        @NotNull HallType type,
        @NotNull @Min(1) @Max(50) Integer rowCount,
        @NotNull @Min(1) @Max(50) Integer columnCount,
        @NotEmpty @Size(max = 1000) List<@Valid SeatCellCreateDTO> seatLayout
) { }
