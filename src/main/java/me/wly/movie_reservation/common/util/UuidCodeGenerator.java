package me.wly.movie_reservation.common.util;

import java.util.UUID;

public class UuidCodeGenerator {
    public static String generateCode(String prefix) {
        String shortUuid = UUID.randomUUID().toString().replace("-","").toLowerCase();
        return prefix + shortUuid;
    }
}
