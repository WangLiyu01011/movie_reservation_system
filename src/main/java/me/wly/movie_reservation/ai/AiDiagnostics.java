package me.wly.movie_reservation.ai;

import dev.langchain4j.exception.*;
import dev.langchain4j.service.output.OutputParsingException;
import lombok.extern.slf4j.Slf4j;
import me.wly.movie_reservation.ai.dto.AiRequestDTO;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import static me.wly.movie_reservation.ai.AiFailureCode.*;

/** Request-scoped diagnostics for the current synchronous AI flow. No raw exception messages are logged. */
@Slf4j
public final class AiDiagnostics implements AutoCloseable {
    private static final ThreadLocal<AiDiagnostics> CURRENT = new ThreadLocal<>();
    private static final Set<String> TOOL_NAMES = Set.of("search_areas", "get_theaters", "get_current_time",
            "get_day_of_week", "get_showing_movies", "get_showing_movies_by_genre", "get_showtime_candidates");
    private final String traceId = UUID.randomUUID().toString();
    private final String sessionId;
    private final String requestId;
    private final AiConversationProperties properties;
    private final LongSupplier ticker;
    private final long started;
    private final AiDiagnostics previous;
    private long generationStarted;
    private long parsingStarted = -1;
    private long toolStarted;
    private int modelCalls;
    private boolean failed;
    private boolean generationActive;
    private AiFailureCode lastFailureCode;

    private AiDiagnostics(AiRequestDTO request, AiConversationProperties properties, LongSupplier ticker) {
        this.sessionId = request == null ? "-" : request.sessionId();
        this.requestId = request == null ? "-" : request.requestId();
        this.properties = properties;
        this.ticker = ticker;
        this.started = ticker.getAsLong();
        this.generationStarted = started;
        this.previous = CURRENT.get();
    }

    public static AiDiagnostics open(AiRequestDTO request, AiConversationProperties properties) {
        return open(request, properties, System::nanoTime);
    }
    static AiDiagnostics open(AiRequestDTO request, AiConversationProperties properties, LongSupplier ticker) {
        var diagnostics = new AiDiagnostics(request, properties, ticker);
        CURRENT.set(diagnostics);
        return diagnostics;
    }
    static AiDiagnostics currentOrDetached(AiConversationProperties properties) {
        var current = CURRENT.get();
        return current == null ? new AiDiagnostics(null, properties, System::nanoTime) : current;
    }
    public String traceId() { return traceId; }

    public <T> T measure(Stage stage, Supplier<T> action) {
        long begin = ticker.getAsLong();
        try {
            T result = action.get();
            success(stage, "-", begin);
            return result;
        } catch (RuntimeException error) {
            failure(classify(error, stage), error, "-", begin);
            throw error;
        }
    }

    void beginGeneration() {
        if (!generationActive) { generationStarted = ticker.getAsLong(); generationActive = true; }
    }
    void checkBudget(boolean startingModelCall) {
        long elapsed = ticker.getAsLong() - generationStarted;
        long required = startingModelCall ? properties.getModelTimeout().toNanos() : 0;
        if (elapsed >= properties.getGenerationTimeout().toNanos()
                || required > properties.getGenerationTimeout().toNanos() - elapsed) {
            throw new AiDiagnosticException(GENERATION_BUDGET_EXCEEDED);
        }
    }
    long modelStarted() { checkBudget(true); modelCalls++; return ticker.getAsLong(); }
    void parsingStarted() { parsingStarted = ticker.getAsLong(); }
    void parsingSucceeded() {
        if (parsingStarted >= 0) success(Stage.PARSING, "-", parsingStarted);
        parsingStarted = -1;
    }
    void toolStarted(String name) { checkBudget(false); toolStarted = ticker.getAsLong(); }
    void toolSucceeded(String name) { success(Stage.TOOL, toolName(name), toolStarted); }
    AiDiagnosticException toolFailed(AiFailureCode code, String name, Throwable error) {
        failure(code, error, toolName(name), toolStarted);
        return new AiDiagnosticException(code, error);
    }
    void success(Stage stage, String tool, long begin) {
        log.info("ai_event traceId={} sessionId={} requestId={} stage={} outcome=SUCCESS elapsedMs={} modelCalls={} tool={}",
                traceId, sessionId, requestId, stage, millis(begin), modelCalls, tool);
    }
    void failure(AiFailureCode code, Throwable error, String tool, long begin) {
        lastFailureCode = code;
        // Do not pass Throwable to the logger: its message may contain keys, prompts, output or SQL values.
        log.warn("ai_event traceId={} sessionId={} requestId={} stage={} outcome=FAILURE errorCode={} elapsedMs={} modelCalls={} tool={} causes={} location={} validationRules={}",
                traceId, sessionId, requestId, code.stage(), code, millis(begin), modelCalls, tool,
                causeTypes(error), location(error), causes(error).stream()
                        .filter(AiDiagnosticException.class::isInstance).map(AiDiagnosticException.class::cast)
                        .flatMap(diagnostic -> diagnostic.validationRules().stream()).limit(12).toList());
    }
    AiFailureException generationFailed(RuntimeException error) {
        var code = lastFailureCode == null ? classify(error, Stage.GENERATION) : lastFailureCode;
        // A stage already logged its own duration. Do not relabel total generation time as that stage's duration.
        if (lastFailureCode == null) {
            failure(code, error, "-", code == OUTPUT_PARSING_FAILED && parsingStarted >= 0 ? parsingStarted : generationStarted);
        }
        failed = true;
        return new AiFailureException(traceId, code, error);
    }
    void requestFailed(RuntimeException error) {
        failed = true;
        if (!(error instanceof AiFailureException) && lastFailureCode == null) {
            failure(classify(error, Stage.STORE), error, "-", started);
        }
    }
    private long millis(long begin) { return Math.max(0, (ticker.getAsLong() - begin) / 1_000_000); }
    private String toolName(String name) { return TOOL_NAMES.contains(name) ? name : "UNKNOWN"; }

    static AiFailureCode classify(Throwable error, Stage fallback) {
        var causes = causes(error);
        for (var cause : causes) if (cause instanceof AiDiagnosticException diagnostic) return diagnostic.code();
        if (causes.stream().anyMatch(OutputParsingException.class::isInstance)) return OUTPUT_PARSING_FAILED;
        if (fallback == Stage.MODEL) {
            for (var cause : causes) {
                if (cause instanceof dev.langchain4j.exception.TimeoutException || cause instanceof SocketTimeoutException
                        || cause instanceof HttpTimeoutException || cause instanceof java.util.concurrent.TimeoutException
                        || Set.of("io.netty.handler.timeout.ReadTimeoutException", "io.netty.channel.ConnectTimeoutException")
                        .contains(cause.getClass().getName())) return MODEL_TIMEOUT;
                if (cause instanceof dev.langchain4j.exception.RateLimitException) return MODEL_RATE_LIMITED;
                if (cause instanceof AuthenticationException) return MODEL_AUTHENTICATION_FAILED;
                if (cause instanceof ModelNotFoundException) return MODEL_NOT_FOUND;
                if (cause instanceof InvalidRequestException) return MODEL_INVALID_REQUEST;
                if (cause instanceof InternalServerException || cause instanceof UnresolvedModelServerException) return MODEL_UNAVAILABLE;
            }
            return MODEL_FAILED;
        }
        if (causes.stream().anyMatch(cause -> cause.getClass() == RuntimeException.class && cause.getMessage() != null
                && cause.getMessage().startsWith("Something is wrong, exceeded ")
                && cause.getMessage().contains("tool calling round trips"))) return TOOL_ROUND_LIMIT;
        return switch (fallback) {
            case SETUP -> ASSISTANT_SETUP_FAILED;
            case MEMORY -> MEMORY_SERIALIZATION_FAILED;
            case STORE -> CONVERSATION_STORE_FAILED;
            default -> GENERATION_FAILED;
        };
    }
    private static List<Throwable> causes(Throwable error) {
        var result = new ArrayList<Throwable>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (error != null && result.size() < 12 && visited.add(error)) {
            result.add(error);
            error = error.getCause();
        }
        return result;
    }
    private static List<String> causeTypes(Throwable error) {
        return causes(error).stream().map(cause -> cause.getClass().getName()).toList();
    }
    private static String location(Throwable error) {
        var chain = causes(error);
        Collections.reverse(chain);
        for (var cause : chain) for (var frame : cause.getStackTrace()) {
            if (frame.getClassName().startsWith("me.wly.movie_reservation.")) {
                return frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
            }
        }
        return "-";
    }
    @Override public void close() {
        log.info("ai_event traceId={} sessionId={} requestId={} stage=REQUEST outcome={} elapsedMs={} modelCalls={} modelTimeoutMs={} generationTimeoutMs={} thinkingMode={}",
                traceId, sessionId, requestId, failed ? "FAILURE" : "SUCCESS", millis(started), modelCalls,
                properties.getModelTimeout().toMillis(), properties.getGenerationTimeout().toMillis(), properties.getThinkingMode());
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
