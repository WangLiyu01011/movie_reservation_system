package me.wly.movie_reservation.model.vo;

public record TheaterAdminVO(
        Long id,
        Integer theaterId,
        Long userId,
        String username
) {
}
