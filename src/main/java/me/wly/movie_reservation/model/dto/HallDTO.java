package me.wly.movie_reservation.model.dto;

import me.wly.movie_reservation.model.enum_class.HallType;

import java.util.List;

public record HallDTO(
        Integer id,
        Integer theaterId,
        String name,
        HallType type,
        String status,
        Integer rowCount,
        Integer columnCount,
        List<SeatCellDTO> seats
) { }
