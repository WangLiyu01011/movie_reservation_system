package me.wly.movie_reservation.model.vo;

import java.time.LocalDateTime;

public record MovieCardVO(
    String title,
    LocalDateTime releaseDate,
    String posterImageURL
) {}
