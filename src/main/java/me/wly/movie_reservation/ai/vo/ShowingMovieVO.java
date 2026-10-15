package me.wly.movie_reservation.ai.vo;

import java.util.List;

public record ShowingMovieVO(
        Integer movieId,
        String imdbId,
        String title,
        String description,
        List<String> genres,
        String language,
        String posterImageUrl
) {
}
