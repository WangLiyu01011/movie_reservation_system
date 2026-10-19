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
    @NotNull private Duration generationTimeout = Duration.ofMinutes(3);
    @NotNull private ThinkingMode thinkingMode = ThinkingMode.DEFAULT;
    @Min(256) @Max(32768) private Integer maxOutputTokens;
    @Min(8) @Max(200) private int maxMessages = 40;
    @Min(1) @Max(200) private int maxTurns = 50;
    @Min(1024) @Max(2097152) private int maxSessionBytes = 524288;
    @Min(1) @Max(10) private int maxToolRoundTrips = 6;

    @AssertTrue(message = "Require positive durations, model timeout <= generation timeout, generation timeout + 5s < lock TTL < session TTL")
    public boolean isTimingValid() {
        return positive(ttl) && positive(lockTtl) && positive(modelTimeout) && positive(generationTimeout)
                && modelTimeout.compareTo(generationTimeout) <= 0
                && generationTimeout.plusSeconds(5).compareTo(lockTtl) < 0 && lockTtl.compareTo(ttl) < 0;
    }
    public enum ThinkingMode { DEFAULT, ENABLED, DISABLED }
    private boolean positive(Duration value) { return value != null && value.toMillis() > 0; }
}
