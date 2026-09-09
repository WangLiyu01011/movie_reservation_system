package me.wly.movie_reservation.movie;

import me.wly.movie_reservation.movie.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer> {
    Optional<Movie> findMovieByImdbId(String imdbId);
    @Query(value = """
            SELECT * FROM movie
            WHERE JSON_SEARCH(LOWER(genres), 'one', LOWER(:genre), NULL, '$[*]') IS NOT NULL
            """, nativeQuery = true)
    List<Movie> findMoviesByGenre(@Param("genre") String genre);
    List<Movie> findMoviesByReleaseDateGreaterThan(LocalDateTime date);
    @Query("select m from Movie m where m.releaseDate <= :now and (m.offDate is null or m.offDate > :now) order by m.releaseDate desc")
    List<Movie> findOnShowingMovies(LocalDateTime now);
}
