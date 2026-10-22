package me.wly.movie_reservation.common.redis;

import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RedisMetricsConfiguration {
    @Bean MeterBinder redisBreakerMetrics(RedisCircuitBreaker breaker) {
        return registry -> registry.gauge("redis.circuit.mode", breaker, RedisCircuitBreaker::modeValue);
    }
}
