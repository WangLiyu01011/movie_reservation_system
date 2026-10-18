package me.wly.movie_reservation.ai.vo;

import java.time.Instant;
import java.util.List;

public record AiSessionVO(String sessionId, Instant expiresAt, List<AiTurnVO> turns) { }
