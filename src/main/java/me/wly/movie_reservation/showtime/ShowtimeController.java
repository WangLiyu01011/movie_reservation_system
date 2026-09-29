package me.wly.movie_reservation.showtime;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.api.ApiResponse;
import me.wly.movie_reservation.showtime.dto.ShowtimeCreateDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchResultDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/showtimes")
@RequiredArgsConstructor
public class ShowtimeController {
    private final ShowtimeService showtimeService;
    private final ShowtimeQueryService showtimeQueryService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ShowtimeSearchResultDTO>> searchShowtime(
            @Valid @ModelAttribute ShowtimeSearchDTO request) {
        return ResponseEntity.ok(ApiResponse.success(showtimeQueryService.searchCandidates(request)));
    }

    @GetMapping()
    public ResponseEntity<ApiResponse<List<ShowtimeDTO>>> getShowtime(@RequestParam Integer theaterId, @RequestParam(required = false) String movieId) {
        return new ResponseEntity<>(ApiResponse.success(showtimeService.getShowtimeList(theaterId, movieId)), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShowtimeDTO>> createShowtime(@Valid @RequestBody ShowtimeCreateDTO newShowtime, @AuthenticationPrincipal UserDetails userDetails) {
        ShowtimeDTO showtimeDTO = showtimeService.createShowtime(newShowtime, userDetails.getUsername());
        return new ResponseEntity<>(ApiResponse.success(showtimeDTO), HttpStatus.CREATED);
    }
}
