package me.wly.movie_reservation.movie.vo;

import java.time.LocalDateTime;

public record MovieVO(
    String title,
    LocalDateTime releaseDate,
    String posterImageURL
) {}
