package me.wly.movie_reservation.order;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "order.redis")
public class OrderRedisProperties {
    private boolean enabled = true;
    @NotNull
    private Duration requestLockTtl = Duration.ofSeconds(60);
    @NotNull
    private Duration rateLimitWindow = Duration.ofSeconds(10);
    @NotNull
    private Duration expireScanDelay = Duration.ofSeconds(1);
    @Min(1)
    private int rateLimitMaxRequests = 3;
    @Min(1)
    private long batchSizeForExpiration = 20;

    @AssertTrue(message = "Order Redis TTL and rate limit window must be at least one hundred millisecond")
    public boolean isDurationConfigurationValid() {
        return requestLockTtl != null && requestLockTtl.toMillis() >= 100
                && rateLimitWindow != null && rateLimitWindow.toMillis() >= 100;
    }
}
