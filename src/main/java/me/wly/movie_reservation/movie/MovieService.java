package me.wly.movie_reservation.movie;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.movie.vo.MovieDetailVO;
import me.wly.movie_reservation.movie.vo.MovieVO;
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
    private final MovieCacheService movieCacheService;

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

    public MovieDetailVO singleMovie(String imdbId) {
        String normalizedImdbId = imdbId.strip();
        MovieDetailVO movie = movieCacheService.getOrLoadDetail(
                normalizedImdbId,
                () -> movieRepository.findMovieByImdbId(normalizedImdbId)
                        .map(movieMapper::entityToDetailVO)
                        .orElse(null)
        );
        if (movie == null) {
            throw movieNotFound(normalizedImdbId);
        }
        return movie;
    }

    public List<Movie> getUpcomingMovies(){
        LocalDateTime now = LocalDateTime.now();
        return movieRepository.findMoviesByReleaseDateGreaterThan(now);
    }

    public List<Movie> getShowingMovies(){
        LocalDateTime now = LocalDateTime.now();
        return movieRepository.findOnShowingMovies(now);
    }

    private BusinessException movieNotFound(String imdbId) {
        return new BusinessException(
                ResultCode.MOVIE_NOT_FOUND,
                "Movie not found with: " + imdbId
        );
    }

}
