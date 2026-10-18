package me.wly.movie_reservation.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Amount and provider identifiers always come from the server. */
public record RefundRequestDTO(@NotBlank @Size(max = 64) String requestId,
                               @NotBlank @Size(max = 255) String reason) { }
