package me.wly.movie_reservation.model.vo;

import me.wly.movie_reservation.HallType;

import java.util.List;

public record TheaterCardVO (
        String theaterName,
        String location,
        List<HallType> hallTypeContain
){}
