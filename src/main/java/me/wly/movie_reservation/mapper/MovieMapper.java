package me.wly.movie_reservation.mapper;

import me.wly.movie_reservation.model.entity.Movie;
import me.wly.movie_reservation.model.vo.MovieVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MovieMapper {

    @Mapping(target = "title")
    @Mapping(target = "releaseDate")
    @Mapping(target = "posterImageURL")
    MovieVO entityToVO(Movie movie);

}
