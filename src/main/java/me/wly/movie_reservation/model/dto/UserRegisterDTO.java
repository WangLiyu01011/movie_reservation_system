package me.wly.movie_reservation.model.dto;


public record UserRegisterDTO (
        String userName,
        String nickName,
        String emailAddress,
        String phoneNumber,
        String password
)
{}