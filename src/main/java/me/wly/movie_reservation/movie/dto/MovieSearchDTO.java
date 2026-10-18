package me.wly.movie_reservation.movie.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.wly.movie_reservation.movie.model.MovieListingStatus;

public record MovieSearchDTO(
        @NotNull MovieListingStatus status,
        @Size(max = 100) String keyword,
        @Size(max = 32) String genre,
        @Min(0) Integer page,
        @Min(1) @Max(50) Integer size
) {
    public MovieSearchDTO {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
