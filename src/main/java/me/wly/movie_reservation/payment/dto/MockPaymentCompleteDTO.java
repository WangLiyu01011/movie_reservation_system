package me.wly.movie_reservation.payment.dto;

import jakarta.validation.constraints.NotNull;
import me.wly.movie_reservation.payment.model.PaymentCallbackStatus;

public record MockPaymentCompleteDTO(@NotNull PaymentCallbackStatus status, boolean notifyMerchant) { }
