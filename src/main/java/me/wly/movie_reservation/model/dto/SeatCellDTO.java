package me.wly.movie_reservation.model.dto;

import me.wly.movie_reservation.model.enum_class.SeatType;

public record SeatCellDTO(
        Long id,
        Integer row,
        Integer column,
        String seatLabel,
        SeatType seatType
) { }
