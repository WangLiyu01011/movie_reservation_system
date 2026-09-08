package me.wly.movie_reservation.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.wly.movie_reservation.model.enum_class.HallType;

import java.util.List;

public record TheaterCreateDTO(
        @NotNull Integer cityId,
        @NotNull Integer districtId,
        @NotBlank @Size(max = 100) String theaterName,
        @NotBlank @Size(max = 255) String location,
        @NotEmpty @Size(max = 10) List<@NotNull HallType> hallTypesContain
) {
}
