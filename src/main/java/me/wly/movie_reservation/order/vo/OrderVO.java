package me.wly.movie_reservation.order.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderVO(
        String movieTitle,
        String theaterName,
        String hallName,
        List<String> seatLocations,
        BigDecimal totalPrice,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {}
