package me.wly.movie_reservation.model.dto;

import java.time.LocalDate;

public record MovieTheaterDTO(
    Integer city_id,
    Long movie_id,
    LocalDate date,
    Integer district_id
){}
