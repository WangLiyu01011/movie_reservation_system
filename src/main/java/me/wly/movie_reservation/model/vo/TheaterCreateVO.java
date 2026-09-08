package me.wly.movie_reservation.model.vo;

import me.wly.movie_reservation.model.enum_class.HallType;

import java.util.List;

public record TheaterCreateVO(
        Integer id,
        Integer cityId,
        String cityName,
        Integer districtId,
        String districtName,
        String theaterName,
        String location,
        List<HallType> hallTypesContain
) {
}
