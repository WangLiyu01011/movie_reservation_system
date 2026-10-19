package me.wly.movie_reservation.ai.vo;

import me.wly.movie_reservation.movie.model.MovieGenre;
import java.util.List;

/** Each successful tool query returns at most five candidates, never the complete movie catalogue. */
public record ShowingMoviePageVO(List<ShowingMovieVO> items, int page, int size,
                                 boolean hasMore, Integer nextPage, String genre, String error,
                                 List<String> supportedGenres) {
    public ShowingMoviePageVO {
        items = List.copyOf(items);
        supportedGenres = List.copyOf(supportedGenres);
    }

    public static ShowingMoviePageVO invalidGenre() {
        return new ShowingMoviePageVO(List.of(), 0, 5, false, null, null,
                "INVALID_GENRE: 只接受单个合法电影类型关键词；主观风格或否定表达不能直接作为类型。请修正关键词或询问用户。",
                MovieGenre.supportedLabels());
    }
}
