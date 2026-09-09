package me.wly.movie_reservation.theater.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.wly.movie_reservation.theater.model.HallType;

public record HallUpdateDTO(
        @NotBlank @Size(max = 40) String name,
        @NotNull HallType type,
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status
) {
}
