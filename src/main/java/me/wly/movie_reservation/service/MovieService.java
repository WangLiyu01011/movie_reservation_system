package me.wly.movie_reservation.service;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.mapper.MovieMapper;
import me.wly.movie_reservation.model.entity.Movie;
import me.wly.movie_reservation.model.vo.MovieVO;
import me.wly.movie_reservation.repository.MovieRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.common.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class MovieService {
    private final MovieRepository movieRepository;
    private final MovieMapper movieMapper;
    public List<MovieVO>  getMovies(String genre) {
        if(genre == null || genre.isBlank()) {
            List<Movie> movies = movieRepository.findAll();
            return movies.stream().map(entity -> new MovieVO(entity.getTitle(), entity.getReleaseDate(), entity.getPosterImageURL()))
                    .toList();
        }
        else {
            List<Movie> movies = movieRepository.findMoviesByGenre(genre.strip());
            return movies.stream().map(entity -> new MovieVO(entity.getTitle(), entity.getReleaseDate(), entity.getPosterImageURL()))
                    .toList();
        }
    }

    public MovieVO singleMovie(String imdbId){
        Movie movieFound = movieRepository.findMovieByImdbId(imdbId)
                .orElseThrow(()->new BusinessException(ResultCode.MOVIE_NOT_FOUND, "Movie not found with: " + imdbId));
        return movieMapper.entityToVO(movieFound);
    }

    public List<Movie> getUpcomingMovies(){
        LocalDateTime now = LocalDateTime.now();
        return movieRepository.findMoviesByReleaseDateGreaterThan(now);
    }

    public List<Movie> getShowingMovies(){
        LocalDateTime now = LocalDateTime.now();
        return movieRepository.findOnShowingMovies(now);
    }


}
