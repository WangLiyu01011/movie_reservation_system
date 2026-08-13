package me.wly.movie_reservation;

import lombok.Getter;

public enum UserRole {
    SUPER_ADMIN("SUPER_ADMIN","Admin of the reservation platform."),
    THEATER_ADMIN("THEATER_ADMIN","Admin of specific theater."),
    THEATER_STUFF("THEATER_STUFF","Normal stuff of specific theater."),
    CUSTOMER("CUSTOMER","The customers.");

    @Getter
    private final String code;
    @Getter
    private final String description;

    UserRole(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
