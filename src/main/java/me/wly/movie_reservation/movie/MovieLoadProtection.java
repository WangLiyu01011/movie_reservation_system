package me.wly.movie_reservation.movie;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.movie.vo.MovieDetailVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Per-instance protection for read-only movie details, not for inventory/payment decisions. */
@Component
public final class MovieLoadProtection {
    private final MovieCacheProperties properties;
    private final MeterRegistry metrics;
    private final Semaphore databasePermits;
    private final Semaphore flightSlots;
    private final ConcurrentHashMap<String, CompletableFuture<MovieDetailVO>> flights = new ConcurrentHashMap<>();
    private final Map<String, LocalEntry> local = new LinkedHashMap<>();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();
    private record LocalEntry(MovieDetailVO movie, long expiresAtNanos) { }

    public MovieLoadProtection(MovieCacheProperties properties, MeterRegistry metrics) {
        this.properties = properties; this.metrics = metrics;
        databasePermits = new Semaphore(properties.getMaxConcurrentLoads());
        flightSlots = new Semaphore(properties.getMaxInflightKeys());
        metrics.gauge("movie.db.active", active);
        metrics.gauge("movie.db.peak", peak);
        metrics.gauge("movie.singleflight.active", flights, Map::size);
        metrics.gauge("movie.fallback.entries", this, MovieLoadProtection::localSize);
    }

    public MovieDetailVO singleFlight(String key, Supplier<MovieDetailVO> leader) {
        var current = flights.get(key);
        if (current != null) return await(current);
        if (!flightSlots.tryAcquire()) throw rejected("inflight_capacity");
        var mine = new CompletableFuture<MovieDetailVO>();
        current = flights.putIfAbsent(key, mine);
        if (current != null) { flightSlots.release(); return await(current); }
        count("movie.singleflight", "leader");
        try {
            var value = leader.get(); mine.complete(value); return value;
        } catch (RuntimeException | Error failure) {
            mine.completeExceptionally(failure); throw failure;
        } finally {
            flights.remove(key, mine); flightSlots.release();
        }
    }

    private MovieDetailVO await(CompletableFuture<MovieDetailVO> future) {
        count("movie.singleflight", "follower");
        var sample = Timer.start(metrics);
        try {
            return future.get(properties.getFollowerWait().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Never cancel the shared future, and never start another database query.
            throw rejected("follower_timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw rejected("interrupted");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(e.getCause());
        } finally { sample.stop(metrics.timer("movie.singleflight.wait")); }
    }

    public MovieDetailVO database(String reason, Supplier<MovieDetailVO> loader) {
        if (!databasePermits.tryAcquire()) throw rejected("database_capacity");
        peak.accumulateAndGet(active.incrementAndGet(), Math::max);
        var sample = Timer.start(metrics); String outcome = "success";
        try { return loader.get(); }
        catch (RuntimeException | Error failure) { outcome = "failure"; throw failure; }
        finally {
            active.decrementAndGet(); databasePermits.release();
            sample.stop(metrics.timer("movie.db.load", "reason", reason, "outcome", outcome));
        }
    }

    /** Short bounded result cache only used when Redis is unavailable. Null is a cached result too. */
    public MovieDetailVO fallback(String key, Supplier<MovieDetailVO> loader) {
        synchronized (local) {
            var entry = local.get(key);
            if (entry != null && System.nanoTime() - entry.expiresAtNanos() < 0) {
                count("movie.fallback", "local_hit");
                return entry.movie();
            }
            local.remove(key);
        }
        var value = database("redis_unavailable", loader);
        synchronized (local) {
            local.entrySet().removeIf(e -> System.nanoTime() - e.getValue().expiresAtNanos() >= 0);
            if (!local.containsKey(key) && local.size() >= properties.getLocalCapacity()) local.remove(local.keySet().iterator().next());
            local.put(key, new LocalEntry(value, System.nanoTime() + properties.getLocalTtl().toNanos()));
        }
        return value;
    }
    public void count(String name, String outcome) {
        metrics.counter(name, "outcome", outcome).increment();
    }
    public BusinessException rejected(String reason) {
        count("movie.load.rejected", reason);
        return new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Movie details temporarily busy; please retry");
    }
    private int localSize() { synchronized (local) { return local.size(); } }
}
