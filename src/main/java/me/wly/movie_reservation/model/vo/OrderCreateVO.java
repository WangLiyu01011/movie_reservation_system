package me.wly.movie_reservation.model.vo;

import me.wly.movie_reservation.model.enum_class.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderCreateVO(
        String orderCode,
        OrderStatus status,
        BigDecimal totalPrice,
        LocalDateTime expiresAt,

        String movieTitle,
        String theaterName,
        String hallName,
        LocalDateTime startTime,
        LocalDateTime endTime,

        List<OrderSeatVO> seats
) { }
