package me.wly.movie_reservation.theater;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.api.ApiResponse;
import me.wly.movie_reservation.theater.dto.HallCreateDTO;
import me.wly.movie_reservation.theater.dto.HallDTO;
import me.wly.movie_reservation.theater.dto.HallUpdateDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/halls")
@RequiredArgsConstructor
public class HallController {
    private final HallService hallService;

    @PostMapping
    public ResponseEntity<ApiResponse<HallDTO>> createHall(
            @Valid @RequestBody HallCreateDTO dto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        HallDTO hall = hallService.createHall(dto, userDetails.getUsername());
        return new ResponseEntity<>(ApiResponse.success(hall), HttpStatus.CREATED);
    }

    @PutMapping("/{hallId}")
    public ResponseEntity<ApiResponse<HallDTO>> updateHall(
            @PathVariable Integer hallId,
            @Valid @RequestBody HallUpdateDTO dto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        HallDTO hall = hallService.updateHall(hallId, dto, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(hall));
    }
}
