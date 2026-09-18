package me.wly.movie_reservation.common.exception;

import lombok.Getter;

@Getter
public class RateLimitException extends BusinessException {
    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterMillis) {
        super(ResultCode.TOO_MANY_REQUESTS, "Too many order creation attempts; please retry later");
        this.retryAfterSeconds = Math.max(1, (retryAfterMillis + 999) / 1000);
    }

}
