package me.wly.movie_reservation.ai;

/** Cause is available to the debugger but is never blindly rendered into logs or HTTP responses. */
public final class AiDiagnosticException extends IllegalArgumentException {
    private final AiFailureCode code;
    private final java.util.List<String> validationRules;
    public AiDiagnosticException(AiFailureCode code) { this(code, null); }
    public AiDiagnosticException(AiFailureCode code, Throwable cause) {
        this(code, cause, java.util.List.of());
    }
    public AiDiagnosticException(AiFailureCode code, Throwable cause, java.util.List<String> validationRules) {
        super(code.name(), cause);
        this.code = code;
        this.validationRules = java.util.List.copyOf(validationRules);
    }
    public AiFailureCode code() { return code; }
    public java.util.List<String> validationRules() { return validationRules; }
}
