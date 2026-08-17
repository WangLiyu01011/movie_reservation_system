package me.wly.movie_reservation.controller;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.model.vo.MovieVO;
import me.wly.movie_reservation.service.MovieService;
import me.wly.movie_reservation.common.utils.ApiResponse;
import me.wly.movie_reservation.model.entity.Movie;
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
        return new ResponseEntity<ApiResponse<List<MovieVO>>>(ApiResponse.success(movies), HttpStatus.OK);
    }

    @GetMapping("/{imdbId}")
    public ResponseEntity<ApiResponse<MovieVO>> getSingleMovie(@PathVariable String imdbId){

        return new ResponseEntity<ApiResponse<MovieVO>> (ApiResponse.success(movieService.singleMovie(imdbId)), HttpStatus.OK);
    }


    @GetMapping("/showing")
    public ResponseEntity<List<Movie>> getShowingMoviesList() {
        return new ResponseEntity<List<Movie>> (movieService.getShowingMovies(), HttpStatus.OK);
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<Movie>> getUpcomingMoviesList() {
        return new ResponseEntity<List<Movie>> (movieService.getUpcomingMovies(), HttpStatus.OK);
    }
}
