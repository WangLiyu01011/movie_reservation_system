package me.wly.movie_reservation.model.enum_class;

import lombok.Getter;

public enum UserRole {
    SYSTEM_ADMIN("SYSTEM_ADMIN"),
    THEATER_ADMIN("THEATER_ADMIN"),
    CUSTOMER("CUSTOMER");

    @Getter
    private final String code;

    UserRole(String code) {
        this.code = code;
    }
}
