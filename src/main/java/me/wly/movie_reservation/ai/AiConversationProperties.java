package me.wly.movie_reservation.ai;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Component @ConfigurationProperties(prefix = "ai.conversation") @Validated
@Getter @Setter
public class AiConversationProperties {
    @NotNull private Duration ttl = Duration.ofMinutes(30);
    @NotNull private Duration lockTtl = Duration.ofMinutes(4);
    @NotNull private Duration modelTimeout = Duration.ofSeconds(30);
    @Min(8) @Max(200) private int maxMessages = 40;
    @Min(1) @Max(200) private int maxTurns = 50;
    @Min(1024) @Max(2097152) private int maxSessionBytes = 524288;
    @Min(1) @Max(10) private int maxToolRoundTrips = 6;

    @AssertTrue(message = "Conversation durations must be positive and lock TTL must be shorter than session TTL")
    public boolean isTimingValid() {
        return positive(ttl) && positive(lockTtl) && positive(modelTimeout) && lockTtl.compareTo(ttl) < 0;
    }
    private boolean positive(Duration value) { return value != null && value.toMillis() > 0; }
}
