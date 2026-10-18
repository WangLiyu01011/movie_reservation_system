package me.wly.movie_reservation.ai.vo;

import java.time.Instant;

public record AiChatReplyVO(String sessionId, String requestId, Instant expiresAt, AiResponseVO response) { }
