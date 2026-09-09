package me.wly.movie_reservation.common.exception;

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
    INTERNAL_SERVER_ERROR(500,"Internal Server Error");

    private final int code;
    private final String message;

    ResultCode(int code, String message){
        this.code = code;
        this.message = message;
    }

    public int getCode() {
       return this.code;
    }

    public String getMessage() {
        return this.message;
    }
}
