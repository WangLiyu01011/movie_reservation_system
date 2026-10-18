package me.wly.movie_reservation.movie;

import me.wly.movie_reservation.movie.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Shared by content and count queries so totals use exactly the same filters.
    String SEARCH_FILTERS = """
             AND (:titlePattern IS NULL OR m.title LIKE :titlePattern ESCAPE '!')
             AND (:genre IS NULL OR JSON_CONTAINS(LOWER(m.genres), JSON_QUOTE(LOWER(:genre))) = 1)
            """;
    String SHOWING_WINDOW = " WHERE m.release_date <= :now AND (m.off_date IS NULL OR m.off_date > :now) ";
    String UPCOMING_WINDOW = " WHERE m.release_date > :now ";

    @Query(value = "SELECT m.* FROM movie m" + SHOWING_WINDOW + SEARCH_FILTERS
            + " ORDER BY m.release_date DESC, m.id DESC",
            countQuery = "SELECT COUNT(*) FROM movie m" + SHOWING_WINDOW + SEARCH_FILTERS,
            nativeQuery = true)
    Page<Movie> searchShowing(@Param("now") LocalDateTime now, @Param("titlePattern") String titlePattern,
                              @Param("genre") String genre, Pageable pageable);

    @Query(value = "SELECT m.* FROM movie m" + UPCOMING_WINDOW + SEARCH_FILTERS
            + " ORDER BY m.release_date , m.id",
            countQuery = "SELECT COUNT(*) FROM movie m" + UPCOMING_WINDOW + SEARCH_FILTERS,
            nativeQuery = true)
    Page<Movie> searchUpcoming(@Param("now") LocalDateTime now, @Param("titlePattern") String titlePattern,
                               @Param("genre") String genre, Pageable pageable);
}
