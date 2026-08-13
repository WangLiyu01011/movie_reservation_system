package me.wly.movie_reservation.common.exception;

public enum ResultCode {
    SUCCESS(200,"操作成功"),
    USER_NOT_FOUND(40401,"用户不存在"),
    MOVIE_NOT_FOUND(40402,"电影不存在"),
    INTERNAL_SERVER_ERROR(500,"服务器异常");

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
