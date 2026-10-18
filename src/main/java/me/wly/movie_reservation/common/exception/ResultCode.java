package me.wly.movie_reservation.common.exception;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200,"Success"),
    BAD_REQUEST(400,"Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    USER_NOT_FOUND(40401,"User Not Found"),
    MOVIE_NOT_FOUND(40402,"Movie Not Found"),
    THEATER_NOT_FOUND(40403,"Theater Not Found"),
    HALL_NOT_FOUND(40404,"Hall Not Found"),
    ORDER_NOT_FOUND(40405,"Order Not Found"),
    AI_SESSION_NOT_FOUND(40406, "AI session not found or expired"),
    REQUEST_IN_PROGRESS(40901, "Order Request In Progress"),
    IDEMPOTENCY_CONFLICT(40902, "Request ID Already Used For Different Order"),
    AI_SESSION_BUSY(40903, "AI session is generating a response; retry later"),
    AI_REQUEST_CONFLICT(40904, "AI request ID reused with different content"),
    AI_SESSION_LIMIT(40905, "AI session limit reached; start a new session"),
    TOO_MANY_REQUESTS(429, "Too Many Requests"),
    SERVICE_UNAVAILABLE(503, "Service temporarily unavailable"),
    INTERNAL_SERVER_ERROR(500,"Internal Server Error");

    private final int code;
    private final String message;

    ResultCode(int code, String message){
        this.code = code;
        this.message = message;
    }

}
