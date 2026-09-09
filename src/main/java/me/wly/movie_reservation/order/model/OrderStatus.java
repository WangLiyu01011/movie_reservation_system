package me.wly.movie_reservation.order.model;

import lombok.Getter;

@Getter
public enum OrderStatus {
    PENDING_PAYMENT("PENDING_PAYMENT"),
    PAID("PAID"),
    CANCELLED("CANCELLED"),
    EXPIRED("EXPIRED"),
    REFUNDING("REFUNDING"),
    REFUNDED("REFUNDED");

    private final String status;

    OrderStatus(String status) {
        this.status = status;
    }

}
