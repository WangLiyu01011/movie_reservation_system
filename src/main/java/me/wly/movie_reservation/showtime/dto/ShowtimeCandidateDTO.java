package me.wly.movie_reservation.showtime.dto;

import me.wly.movie_reservation.theater.model.HallType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeCandidateDTO(
        Long showtimeId,
        Integer movieId,
        String imdbId,
        String movieTitle,
        Integer theaterId,
        String theaterName,
        String theaterAddress,
        Integer cityId,
        String cityName,
        Integer districtId,
        String districtName,
        Integer hallId,
        String hallName,
        HallType hallType,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal price,
        long availableSeatCount
) {
    public ShowtimeCandidateDTO withAvailableSeatCount(long count) {
        return new ShowtimeCandidateDTO(showtimeId, movieId, imdbId, movieTitle,
                theaterId, theaterName, theaterAddress, cityId, cityName, districtId,
                districtName, hallId, hallName, hallType, startTime, endTime, price, count);
    }
}
