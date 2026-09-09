package me.wly.movie_reservation.common.utils;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> businessExceptionHandler(BusinessException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", e.resultCode.getCode());
        response.put("message", e.getMessage());
        response.put("data", null);
        return ResponseEntity.status(toHttpStatus(e.resultCode)).body(response);
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
            case USER_NOT_FOUND, MOVIE_NOT_FOUND, THEATER_NOT_FOUND, HALL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
