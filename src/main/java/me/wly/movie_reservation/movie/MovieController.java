package me.wly.movie_reservation.movie;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.api.ApiResponse;
import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.movie.vo.MovieDetailVO;
import me.wly.movie_reservation.movie.vo.MovieVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
//@CrossOrigin(origins = "http://localhost:5173",
//        allowedHeaders = "*",
//        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS},
//        allowCredentials = "true")
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {
    private final MovieService movieService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MovieVO>>> getMovies(@RequestParam(required = false) String genre){
        List<MovieVO> movies = movieService.getMovies(genre);
        return new ResponseEntity<>(ApiResponse.success(movies), HttpStatus.OK);
    }

    @GetMapping("/{imdbId}")
    public ResponseEntity<ApiResponse<MovieDetailVO>> getSingleMovie(@PathVariable String imdbId){

        return new ResponseEntity<> (ApiResponse.success(movieService.singleMovie(imdbId)), HttpStatus.OK);
    }


    @GetMapping("/showing")
    public ResponseEntity<List<MovieVO>> getShowingMoviesList() {
        return new ResponseEntity<> (movieService.getShowingMovies(), HttpStatus.OK);
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<MovieVO>> getUpcomingMoviesList() {
        return new ResponseEntity<>(movieService.getUpcomingMovies(), HttpStatus.OK);
    }
}
