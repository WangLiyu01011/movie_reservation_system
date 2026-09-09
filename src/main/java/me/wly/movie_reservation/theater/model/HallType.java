package me.wly.movie_reservation.theater.model;

import lombok.Getter;

public enum HallType {
    BASIC("BASIC", "Basic hall"),
    IMAX("IMAX", "IMAX hall"),
    DOLBY("DOLBY", "Dolby hall"),
    CGS("CGS", "China giant screen"),
    CINITY("CINITY", "Chinese high resolve system.");

    @Getter
    private String code;
    @Getter
    private String description;

    private HallType(String code, String description) {
        this.code = code;
        this.description = description;
    }

}
