package me.wly.movie_reservation.model.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserRegisterDTO (
        @NotBlank(message = "Username cannot be empty. ")
        String username,
        String nickname,
        @Email(message = "Invalid email address.")
        String emailAddress,
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "Invalid phone number")
        String phoneNumber,
        @NotBlank(message = "Password cannot be empty")
        String password
)
{}