package me.wly.movie_reservation.order;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.GlobalExceptionHandler;
import me.wly.movie_reservation.common.exception.RateLimitException;
import me.wly.movie_reservation.common.exception.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderHttpStatusTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void duplicateInFlight_returns409() {
        var response = handler.businessExceptionHandler(new BusinessException(ResultCode.REQUEST_IN_PROGRESS));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void reusedRequestWithDifferentPayload_returns409() {
        var response = handler.businessExceptionHandler(new BusinessException(ResultCode.IDEMPOTENCY_CONFLICT));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void rateLimit_returns429WithRetryAfter() {
        var response = handler.businessExceptionHandler(new RateLimitException(1501));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("2", response.getHeaders().getFirst("Retry-After"));
        assertEquals(429, response.getBody().get("code"));
    }
}
