package me.wly.movie_reservation.theater;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.api.ApiResponse;
import me.wly.movie_reservation.theater.dto.TheaterCreateDTO;
import me.wly.movie_reservation.theater.vo.TheaterCreateVO;
import me.wly.movie_reservation.theater.vo.TheaterVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/theaters")
@RequiredArgsConstructor
public class TheaterController {
    private final TheaterService theaterService;

    @PostMapping()
    public ResponseEntity<ApiResponse<TheaterCreateVO>> createTheater(@Valid @RequestBody TheaterCreateDTO dto) {
        TheaterCreateVO theater = theaterService.createTheater(dto);
        return new ResponseEntity<>(ApiResponse.success(theater), HttpStatus.CREATED);
    }


    @GetMapping()
    public ResponseEntity<ApiResponse<List<TheaterVO>>> getTheatersInDistinct(@RequestParam(required = false) Integer cityId, @RequestParam(required = false) Integer districtId) {
        List<TheaterVO> theaterCards = theaterService.getTheaters(cityId, districtId);
        return new ResponseEntity<ApiResponse<List<TheaterVO>>>(ApiResponse.success(theaterCards), HttpStatus.OK);
    }





}
