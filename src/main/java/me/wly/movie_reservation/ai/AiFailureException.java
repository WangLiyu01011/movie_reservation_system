package me.wly.movie_reservation.ai;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;

/** Keeps the existing public error contract while adding a safe, searchable diagnostic reference. */
public final class AiFailureException extends BusinessException {
    private final String traceId;
    private final AiFailureCode errorCode;
    public AiFailureException(String traceId, AiFailureCode code, Throwable cause) {
        super(ResultCode.INTERNAL_SERVER_ERROR, "AI response unavailable or invalid; please retry");
        this.traceId = traceId;
        this.errorCode = code;
        initCause(cause);
    }
    public String traceId() { return traceId; }
    public AiFailureCode errorCode() { return errorCode; }
}
