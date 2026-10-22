package me.wly.movie_reservation.movie.vo;

import java.time.LocalDateTime;
import java.util.List;

/** Includes identifiers so list cards can link to the existing movie-detail endpoint. */
public record MovieListItemVO(Integer id, String imdbId, String title, LocalDateTime releaseDate,
                              List<String> genres, String language, String posterImageURL) {
    public MovieListItemVO {
        genres = genres == null ? List.of() : List.copyOf(genres);
    }
}
