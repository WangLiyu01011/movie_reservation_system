package me.wly.movie_reservation.model.vo;

import me.wly.movie_reservation.model.enum_class.HallType;

import java.util.List;

public record TheaterVO(
        String theaterName,
        String location,
        List<HallType> hallTypeContain
){}
