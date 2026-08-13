package me.wly.movie_reservation.common.exception;


public class BusinessException extends RuntimeException{
    public final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String customMessage){
        super(customMessage);
        this.resultCode = resultCode;
    }

}
