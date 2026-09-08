package me.wly.movie_reservation.model.vo;

import me.wly.movie_reservation.model.enum_class.SeatType;

import java.math.BigDecimal;

public record OrderSeatVO(
        String seatLabel,
        SeatType seatType,
        BigDecimal ticketPrice
) { }
