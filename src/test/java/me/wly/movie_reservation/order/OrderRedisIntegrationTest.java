package me.wly.movie_reservation.order;

import me.wly.movie_reservation.common.exception.RateLimitException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Starts a disposable local Redis, never connects to the developer's existing instance. */
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_INTEGRATION_TESTS", matches = "true")
class OrderRedisIntegrationTest {
    @TempDir static Path directory;
    private static Process server;
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void startRedis() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String executable = System.getenv().getOrDefault("REDIS_SERVER_EXECUTABLE", "redis-server");
        server = new ProcessBuilder(executable, "--bind", "127.0.0.1", "--port", String.valueOf(port),
                "--save", "", "--appendonly", "no", "--dir", directory.toString())
                .redirectErrorStream(true)
                .redirectOutput(directory.resolve("redis.log").toFile())
                .start();

        boolean ready = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (server.isAlive() && System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 100);
                ready = true;
                break;
            } catch (java.io.IOException exception) {
                Thread.sleep(25);
            }
        }
        assertTrue(ready, "Disposable Redis did not start; see " + directory.resolve("redis.log"));
        factory = new LettuceConnectionFactory("127.0.0.1", port);
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (factory != null) {
            factory.destroy();
        }
        if (server != null) {
            server.destroy();
            if (!server.waitFor(2, TimeUnit.SECONDS)) {
                server.destroyForcibly();
                server.waitFor(2, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void concurrentDuplicateRequests_onlyOneAcquiresPermit() throws Exception {
        OrderCacheService service = new OrderCacheService(redis, new OrderRedisProperties());
        List<Callable<OrderCacheService.RequestPermit>> requests = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            requests.add(() -> service.acquireRequest(101L, "same-request"));
        }
        try (var pool = Executors.newFixedThreadPool(16)) {
            int winners = 0;
            for (var future : pool.invokeAll(requests)) {
                if (future.get().state() == OrderCacheService.RequestState.ACQUIRED) {
                    winners++;
                }
            }
            assertEquals(1, winners);
            assertTrue(redis.getExpire("order:idem:{101}:same-request", TimeUnit.MILLISECONDS) > 0);
        }
    }

    @Test
    void concurrentRateLimit_luaAllowsExactlyThreeOfOneHundredRequests() throws Exception {
        OrderCacheService service = new OrderCacheService(redis, new OrderRedisProperties());
        List<Callable<Boolean>> requests = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            requests.add(() -> {
                try {
                    service.checkRateLimit(102L);
                    return true;
                } catch (RateLimitException exception) {
                    return false;
                }
            });
        }
        try (var pool = Executors.newFixedThreadPool(16)) {
            int allowed = 0;
            for (var future : pool.invokeAll(requests)) {
                if (future.get()) {
                    allowed++;
                }
            }
            assertEquals(3, allowed);
        }
        assertEquals(3L, redis.opsForZSet().zCard("order:rate:create:{102}"));
        assertTrue(redis.getExpire("order:rate:create:{102}", TimeUnit.MILLISECONDS) > 0);
        assertDoesNotThrow(() -> service.checkRateLimit(103L));
    }

    @Test
    void releaseOldOwner_doesNotDeleteNewOwnersLock() {
        OrderRedisProperties properties = new OrderRedisProperties();
        properties.setRequestLockTtl(Duration.ofMillis(100));
        OrderCacheService service = new OrderCacheService(redis, properties);
        var old = service.acquireRequest(104L, "request");
        assertEquals(OrderCacheService.RequestState.ACQUIRED, old.state());
        awaitKeyExpiry(old.key());

        properties.setRequestLockTtl(Duration.ofSeconds(5));
        var current = service.acquireRequest(104L, "request");
        assertEquals(OrderCacheService.RequestState.ACQUIRED, current.state());
        service.releaseRequest(old);
        assertEquals(current.token(), redis.opsForValue().get(current.key()));
        service.releaseRequest(current);
        assertNull(redis.opsForValue().get(current.key()));
    }

    @Test
    void rateLimit_recoversAfterWindowExpires() {
        OrderRedisProperties properties = new OrderRedisProperties();
        properties.setRateLimitWindow(Duration.ofMillis(200));
        properties.setRateLimitMaxRequests(1);
        OrderCacheService service = new OrderCacheService(redis, properties);
        service.checkRateLimit(105L);
        assertThrows(RateLimitException.class, () -> service.checkRateLimit(105L));
        awaitKeyExpiry("order:rate:create:{105}");

        assertDoesNotThrow(() -> service.checkRateLimit(105L));
    }

    private void awaitKeyExpiry(String key) {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (Boolean.TRUE.equals(redis.hasKey(key))) {
                Thread.sleep(10);
            }
        });
    }
}
