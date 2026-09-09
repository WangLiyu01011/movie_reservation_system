package me.wly.movie_reservation.showtime;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.api.ApiResponse;
import me.wly.movie_reservation.showtime.dto.ShowtimeCreateDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/showtimes")
@RequiredArgsConstructor
public class ShowtimeController {
    private final ShowtimeService showtimeService;

    @GetMapping()
    public ResponseEntity<ApiResponse<List<ShowtimeDTO>>> getShowtime(@RequestParam Integer theaterId, @RequestParam(required = false) String movieId) {
        return new ResponseEntity<ApiResponse<List<ShowtimeDTO>>>(ApiResponse.success(showtimeService.getShowtimeList(theaterId, movieId)), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShowtimeDTO>> createShowtime(@Valid @RequestBody ShowtimeCreateDTO newShowtime) {
        return new ResponseEntity<ApiResponse<ShowtimeDTO>>(ApiResponse.success(showtimeService.createShowtime(newShowtime)), HttpStatus.CREATED);
    }
}
