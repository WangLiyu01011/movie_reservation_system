package me.wly.movie_reservation.common.utils;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Map<String, Object> businessExceptionHandler(BusinessException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", e.resultCode.getCode());
        response.put("message", e.resultCode.getMessage());
        response.put("data", null);
        return response;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> otherExceptionHandler(Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", ResultCode.INTERNAL_SERVER_ERROR.getCode());
        response.put("message", e.getMessage());
        response.put("data", null);
        return response;
    }
}
