package me.wly.movie_reservation.model.dto;

import jakarta.validation.constraints.NotNull;

public record TheaterAdminAssignDTO(@NotNull Long userId) {
}
