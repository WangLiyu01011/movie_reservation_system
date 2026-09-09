package me.wly.movie_reservation.order.vo;

import me.wly.movie_reservation.theater.model.SeatType;

import java.math.BigDecimal;

public record OrderSeatVO(
        String seatLabel,
        SeatType seatType,
        BigDecimal ticketPrice
) { }
