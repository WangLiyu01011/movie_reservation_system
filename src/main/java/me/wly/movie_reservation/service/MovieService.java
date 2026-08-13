package me.wly.movie_reservation.service;

import me.wly.movie_reservation.model.entity.Movie;
import me.wly.movie_reservation.model.vo.MovieCardVO;
import me.wly.movie_reservation.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.common.exception.BusinessException;

@Service
public class MovieService {
    @Autowired
    private MovieRepository movieRepository;

    public List<MovieCardVO>  allMovies() {
        List<Movie> movies = movieRepository.findAll();
        return movies.stream().map(entity -> new MovieCardVO(entity.getTitle(), entity.getReleaseDate(), entity.getPosterImageURL()))
                .toList();
    }

    public Movie singleMovie(String imdbId){
        return movieRepository.findMovieByImdbId(imdbId)
                .orElseThrow(()->new BusinessException(ResultCode.MOVIE_NOT_FOUND, "Movie not found with: " + imdbId));
    }

    public List<Movie> genreMovies(String genre){
        return movieRepository.findMoviesByGenresContaining(genre);
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
