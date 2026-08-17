package me.wly.movie_reservation.common.utils;

import java.util.UUID;

public class generateUuidCode {
    public static String setCode(String prefix) {
        String shortUuid = UUID.randomUUID().toString().replace("-","").toLowerCase();
        return prefix + shortUuid;
    }
}
