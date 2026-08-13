package me.wly.movie_reservation.repository;

import me.wly.movie_reservation.model.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer> {
    Optional<Movie> findMovieByImdbId(String imdbId);
    List<Movie> findMoviesByGenresContaining(String genre);
    List<Movie> findMoviesByReleaseDateGreaterThan(LocalDateTime date);
    @Query("select m from Movie m where m.releaseDate <= :now and (m.offDate is null or m.offDate > :now) order by m.releaseDate desc")
    List<Movie> findOnShowingMovies(LocalDateTime now);
}
