package me.wly.movie_reservation.movie.vo;

import java.time.LocalDateTime;
import java.util.List;

public record MovieDetailVO(
        String title,
        LocalDateTime releaseDate,
        String description,
        List<String> genres,
        String language,
        String posterImageURL
) {}
