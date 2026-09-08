package me.wly.movie_reservation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.utils.ApiResponse;
import me.wly.movie_reservation.model.dto.HallCreateDTO;
import me.wly.movie_reservation.model.dto.HallDTO;
import me.wly.movie_reservation.service.HallService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/halls")
@RequiredArgsConstructor
public class HallController {
    private final HallService hallService;

    @PostMapping
    public ResponseEntity<ApiResponse<HallDTO>> createHall(@Valid @RequestBody HallCreateDTO dto) {
        HallDTO hall = hallService.createHall(dto);
        return new ResponseEntity<>(ApiResponse.success(hall), HttpStatus.CREATED);
    }
}
