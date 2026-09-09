package me.wly.movie_reservation.model.vo;

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
