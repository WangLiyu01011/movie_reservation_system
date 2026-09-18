package me.wly.movie_reservation.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validationExceptionHandler(MethodArgumentNotValidException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", ResultCode.BAD_REQUEST.getCode());
        response.put("message", "Invalid request parameters");
        response.put("data", null);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> businessExceptionHandler(BusinessException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", e.resultCode.getCode());
        response.put("message", e.getMessage());
        response.put("data", null);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(toHttpStatus(e.resultCode));
        if (e instanceof RateLimitException rateLimitException) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimitException.getRetryAfterSeconds()));
        }
        return builder.body(response);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> internalServerExceptionHandler(Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", ResultCode.INTERNAL_SERVER_ERROR.getCode());
        response.put("message", e.getMessage());
        response.put("data", null);
        return response;
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> badCredentialsExceptionHandler(BadCredentialsException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", ResultCode.UNAUTHORIZED.getCode());
        response.put("message", e.getMessage());
        response.put("data", null);
        return response;
    }

    private HttpStatus toHttpStatus(ResultCode resultCode) {
        return switch (resultCode) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case REQUEST_IN_PROGRESS, IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case USER_NOT_FOUND, MOVIE_NOT_FOUND, THEATER_NOT_FOUND, HALL_NOT_FOUND, ORDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
