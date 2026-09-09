package me.wly.movie_reservation.theater.vo;

public record TheaterCreateVO(
        Integer id,
        Integer cityId,
        String cityName,
        Integer districtId,
        String districtName,
        String theaterName,
        String location
) {
}
