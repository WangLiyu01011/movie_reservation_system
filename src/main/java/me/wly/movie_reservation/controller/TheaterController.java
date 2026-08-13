package me.wly.movie_reservation.controller;

import me.wly.movie_reservation.common.utils.ApiResponse;
import me.wly.movie_reservation.model.vo.TheaterCardVO;
import me.wly.movie_reservation.service.TheaterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/theaters")
public class TheaterController {
    @Autowired
    private TheaterService theaterService;

//    @GetMapping("/{cityId}")
//    public ResponseEntity<ApiResponse<List<TheaterCardVO>>> getTheatersInCity(@PathVariable Integer cityId) {
//        List<TheaterCardVO> theaterCards = theaterService.getTheatersInCity(cityId);
//        return new ResponseEntity<ApiResponse<List<TheaterCardVO>>>(ApiResponse.success(theaterCards), HttpStatus.OK);
//    }

    @GetMapping("/{districtId}")
    public ResponseEntity<ApiResponse<List<TheaterCardVO>>> getTheatersInDistinct(@PathVariable Integer districtId) {
        List<TheaterCardVO> theaterCards = theaterService.getTheatersInDistrict(districtId);
        return new ResponseEntity<ApiResponse<List<TheaterCardVO>>>(ApiResponse.success(theaterCards), HttpStatus.OK);
    }

}
