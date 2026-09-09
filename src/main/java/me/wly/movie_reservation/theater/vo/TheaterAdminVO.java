package me.wly.movie_reservation.theater.vo;

public record TheaterAdminVO(
        Long id,
        Integer theaterId,
        Long userId,
        String username
) {
}
