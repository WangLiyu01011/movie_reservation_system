package me.wly.movie_reservation.theater.dto;

import jakarta.validation.constraints.NotNull;

public record TheaterAdminAssignDTO(@NotNull Long userId) {
}
