package me.wly.movie_reservation.common.redis;


import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@Component
public final class RedisCircuitBreaker {

    private enum Mode {
        CLOSED, OPEN, HALF_OPEN
    }

    // 每次切换都创建新对象，用来识别请求所属的状态轮次
    public record Permit(Snapshot snapshot) {}

    private record Snapshot(Mode mode, long retryAtNanos) {}

    private final long cooldownNanos = Duration.ofSeconds(5).toNanos();

    // 保证状态更新的线程安全
    private final AtomicReference<Snapshot> state =
            new AtomicReference<>(new Snapshot(Mode.CLOSED, 0));

    /** Observability only: CLOSED=0, OPEN=1, HALF_OPEN=2. */
    public int modeValue() { return state.get().mode().ordinal(); }

    // 返回null表示当前请求应跳过Redis
    public Permit tryAcquire() {
        while (true) {
            Snapshot current = state.get();

            if (current.mode() == Mode.CLOSED) {
                return new Permit(current);
            }

            if (current.mode() == Mode.HALF_OPEN) {
                return null;
            }

            // 使用单调时间计算间隔，不受系统时间调整影响
            if (System.nanoTime() - current.retryAtNanos() < 0) {
                return null;
            }

            Snapshot probing = new Snapshot(Mode.HALF_OPEN, 0);

            // current与当前实际state不一致则进入下一轮判断
            if (state.compareAndSet(current, probing)) {
                return new Permit(probing);
            }
        }
    }

    public void onSuccess(Permit permit) {
        if (permit.snapshot().mode() == Mode.HALF_OPEN) {
            state.compareAndSet(
                    permit.snapshot(),
                    new Snapshot(Mode.CLOSED, 0)
            );
        }
    }

    // redis 不可用时调用该方法
    public void onFailure(Permit permit) {
        // 预期值是传入的permit
        state.compareAndSet(
                permit.snapshot(),
                new Snapshot(
                        Mode.OPEN,
                        System.nanoTime() + cooldownNanos
                )
        );
    }

    // 防止探测的线程因意外异常退出后，一直停在HALF_OPEN导致后续redis持续不可用
    public void finish(Permit permit) {
        if (permit.snapshot().mode() == Mode.HALF_OPEN) {
            onFailure(permit);
        }
    }
}
