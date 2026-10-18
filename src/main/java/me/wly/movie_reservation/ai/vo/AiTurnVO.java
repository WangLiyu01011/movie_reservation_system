package me.wly.movie_reservation.ai.vo;

import java.time.Instant;

public record AiTurnVO(String requestId, String userMessage, AiResponseVO response, Instant completedAt) { }
