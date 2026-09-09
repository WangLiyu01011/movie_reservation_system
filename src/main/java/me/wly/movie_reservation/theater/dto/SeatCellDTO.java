package me.wly.movie_reservation.theater.dto;

import me.wly.movie_reservation.theater.model.SeatType;

public record SeatCellDTO(
        Long id,
        Integer row,
        Integer column,
        String seatLabel,
        SeatType seatType
) { }
