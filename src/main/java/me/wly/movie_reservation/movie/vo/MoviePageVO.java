package me.wly.movie_reservation.movie.vo;

import java.util.List;

public record MoviePageVO(List<MovieListItemVO> items, int page, int size,
                           long totalElements, int totalPages, boolean hasNext) {
    public MoviePageVO { items = List.copyOf(items); }
}
