package me.wly.movie_reservation.theater;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.api.ApiResponse;
import me.wly.movie_reservation.theater.dto.TheaterAdminAssignDTO;
import me.wly.movie_reservation.theater.vo.TheaterAdminVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/theaters/{theaterId}/admins")
@RequiredArgsConstructor
public class TheaterAdminController {
    private final TheaterAdminService theaterAdminService;

    @PostMapping
    public ResponseEntity<ApiResponse<TheaterAdminVO>> assignAdmin(
            @PathVariable Integer theaterId,
            @Valid @RequestBody TheaterAdminAssignDTO dto
    ) {
        TheaterAdminVO theaterAdmin = theaterAdminService.assignAdmin(theaterId, dto);
        return new ResponseEntity<>(ApiResponse.success(theaterAdmin), HttpStatus.CREATED);
    }
}
