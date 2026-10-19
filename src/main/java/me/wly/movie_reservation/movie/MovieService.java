package me.wly.movie_reservation.movie;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.movie.model.MovieGenre;
import me.wly.movie_reservation.movie.vo.MovieDetailVO;
import me.wly.movie_reservation.movie.vo.MovieVO;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;

import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.common.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class MovieService {
    private final Clock businessClock;
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

    public List<MovieVO> getUpcomingMovies(){
        LocalDateTime now = LocalDateTime.now(businessClock);

        List<Movie> movies = movieRepository.findMoviesByReleaseDateGreaterThan(now);
        return movies.stream().map(entity -> new MovieVO(entity.getTitle(), entity.getReleaseDate(), entity.getPosterImageURL()))
                .toList();
    }

    public List<MovieVO> getShowingMovies(){
        LocalDateTime now = LocalDateTime.now(businessClock);
        List<Movie> movies = movieRepository.findOnShowingMovies(now);
        return movies.stream().map(entity -> new MovieVO(entity.getTitle(), entity.getReleaseDate(), entity.getPosterImageURL()))
                .toList();
    }

    /** AI tools cannot increase the batch size; Slice avoids a catalogue-wide count query. */
    @Transactional(readOnly = true)
    public Slice<Movie> getShowingMoviesOn(LocalDateTime time, Integer page, MovieGenre genre) {
        int pageNumber = page == null ? 0 : page;
        if (time == null || pageNumber < 0 || (long) pageNumber * 5 > Integer.MAX_VALUE - 5) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Invalid movie query time or page");
        }
        return movieRepository.findShowingCandidates(time, genre == null ? null : genre.label(),
                genre == null ? null : genre.englishLabel(), PageRequest.of(pageNumber, 5));
    }

    private BusinessException movieNotFound(String imdbId) {
        return new BusinessException(
                ResultCode.MOVIE_NOT_FOUND,
                "Movie not found with: " + imdbId
        );
    }

}
