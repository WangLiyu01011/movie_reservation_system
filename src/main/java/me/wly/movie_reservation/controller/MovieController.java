package me.wly.movie_reservation.controller;

import me.wly.movie_reservation.model.vo.MovieCardVO;
import me.wly.movie_reservation.service.MovieService;
import me.wly.movie_reservation.common.utils.ApiResponse;
import me.wly.movie_reservation.model.entity.Movie;
import org.springframework.beans.factory.annotation.Autowired;
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
public class MovieController {
    @Autowired
    private MovieService movieService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MovieCardVO>>> getAllMovies(){
        List<MovieCardVO> movies = movieService.allMovies();
        return new ResponseEntity<ApiResponse<List<MovieCardVO>>>(ApiResponse.success(movies), HttpStatus.OK);
    }

    @GetMapping("/{imdbId}")
    public ResponseEntity<Movie> getSingleMovie(@PathVariable String imdbId){
        return new ResponseEntity<Movie> (movieService.singleMovie(imdbId), HttpStatus.OK);
    }

    @GetMapping("/{genre}")
    public ResponseEntity<List<Movie>> getMoviesByGenre(@PathVariable String genre){
        return new ResponseEntity<List<Movie>> (movieService.genreMovies(genre), HttpStatus.OK);
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
