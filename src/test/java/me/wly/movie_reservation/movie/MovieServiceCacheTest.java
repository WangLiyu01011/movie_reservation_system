package me.wly.movie_reservation.movie;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.movie.vo.MovieDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieServiceCacheTest {
    private static final String IMDB_ID = "tt0111161";
    private static final String CACHE_KEY = "movie:v1:detail:" + IMDB_ID;
    private static final String CACHED_JSON = "{\"title\":\"The Shawshank Redemption\"}";

    @Mock
    private MovieRepository movieRepository;
    @Mock
    private MovieMapper movieMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;
    private MovieService movieService;

    @BeforeEach
    void setUpRedisOperations() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        MovieCacheService movieCacheService = new MovieCacheService(stringRedisTemplate, objectMapper);
        movieService = new MovieService(java.time.Clock.system(me.wly.movie_reservation.common.time.BusinessTimeConfiguration.BUSINESS_ZONE), movieRepository, movieMapper, movieCacheService);
    }

    @Test
    void singleMovie_returnsCachedMovieWhenCacheHits() throws Exception {
        MovieDetailVO cachedMovie = movieDetail();
        when(valueOperations.get(CACHE_KEY)).thenReturn(CACHED_JSON);
        when(objectMapper.readValue(CACHED_JSON, MovieDetailVO.class)).thenReturn(cachedMovie);

        MovieDetailVO result = movieService.singleMovie(IMDB_ID);

        assertSame(cachedMovie, result);
        verify(movieRepository, never()).findMovieByImdbId(any());
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void singleMovie_loadsDatabaseAndWritesCacheWhenCacheMisses() throws Exception {
        Movie movie = movie();
        MovieDetailVO databaseMovie = movieDetail();
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(
                eq("lock:movie:v1:detail:" + IMDB_ID),
                any(),
                any(Duration.class)
        )).thenReturn(true);
        when(movieRepository.findMovieByImdbId(IMDB_ID)).thenReturn(Optional.of(movie));
        when(movieMapper.entityToDetailVO(movie)).thenReturn(databaseMovie);
        when(objectMapper.writeValueAsString(databaseMovie)).thenReturn(CACHED_JSON);

        MovieDetailVO result = movieService.singleMovie(IMDB_ID);

        assertSame(databaseMovie, result);
        verify(movieRepository).findMovieByImdbId(IMDB_ID);
        verify(valueOperations).set(eq(CACHE_KEY), eq(CACHED_JSON), any(Duration.class));
    }

    @Test
    void singleMovie_rejectsRequestWithoutDatabaseQueryWhenNullCacheHits() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("__NULL__");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> movieService.singleMovie(IMDB_ID)
        );

        assertTrue(exception.getMessage().contains(IMDB_ID));
        verify(movieRepository, never()).findMovieByImdbId(any());
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void singleMovie_fallsBackToDatabaseWhenRedisIsUnavailable() throws Exception {
        Movie movie = movie();
        MovieDetailVO databaseMovie = movieDetail();
        DataAccessResourceFailureException redisFailure =
                new DataAccessResourceFailureException("Redis is unavailable");
        when(valueOperations.get(CACHE_KEY)).thenThrow(redisFailure);
        when(valueOperations.setIfAbsent(
                eq("lock:movie:v1:detail:" + IMDB_ID),
                any(),
                any(Duration.class)
        )).thenThrow(redisFailure);
        when(movieRepository.findMovieByImdbId(IMDB_ID)).thenReturn(Optional.of(movie));
        when(movieMapper.entityToDetailVO(movie)).thenReturn(databaseMovie);
        when(objectMapper.writeValueAsString(databaseMovie)).thenReturn(CACHED_JSON);
        doThrow(redisFailure)
                .when(valueOperations)
                .set(eq(CACHE_KEY), eq(CACHED_JSON), any(Duration.class));

        MovieDetailVO result = movieService.singleMovie(IMDB_ID);

        assertEquals(databaseMovie, result);
        verify(movieRepository).findMovieByImdbId(IMDB_ID);
        verify(valueOperations).set(eq(CACHE_KEY), eq(CACHED_JSON), any(Duration.class));
    }

    private Movie movie() {
        Movie movie = new Movie();
        movie.setImdbId(IMDB_ID);
        movie.setTitle("The Shawshank Redemption");
        movie.setReleaseDate(LocalDateTime.of(1994, 9, 23, 0, 0));
        movie.setDescription("Two imprisoned men bond over a number of years.");
        movie.setGenres(List.of("Drama"));
        movie.setLanguage("en");
        movie.setPosterImageURL("https://example.com/poster.jpg");
        return movie;
    }

    private MovieDetailVO movieDetail() {
        return new MovieDetailVO(
                "The Shawshank Redemption",
                LocalDateTime.of(1994, 9, 23, 0, 0),
                "Two imprisoned men bond over a number of years.",
                List.of("Drama"),
                "en",
                "https://example.com/poster.jpg"
        );
    }
}
