package me.wly.movie_reservation.movie;

import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.movie.vo.MovieDetailVO;
import me.wly.movie_reservation.movie.vo.MovieVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MovieMapper {

    @Mapping(target = "title")
    @Mapping(target = "releaseDate")
    @Mapping(target = "posterImageURL")
    MovieVO entityToVO(Movie movie);
    MovieDetailVO entityToDetailVO(Movie movie);
}
