package me.wly.movie_reservation.model.vo;

import java.util.List;

public record LoginResultVO(
        String token,
        String tokenHead,
        Long expireTime,
        UserLoginVO userInfo,
        List<String> roles,
        List<String> permissions
) {}
