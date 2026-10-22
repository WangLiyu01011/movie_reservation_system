package me.wly.movie_reservation.movie;

import jakarta.validation.constraints.*;
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
@ConfigurationProperties("movie.cache")
public class MovieCacheProperties {
    @Min(1) private int maxConcurrentLoads = 4;
    @Min(1) private int maxInflightKeys = 128;
    @Min(1) private int localCapacity = 1024;
    @NotNull private Duration followerWait = Duration.ofMillis(1500);
    @NotNull private Duration localTtl = Duration.ofSeconds(2);

    @AssertTrue(message = "Movie cache durations must be positive and at most 30 seconds")
    public boolean isDurationsValid() {
        return followerWait != null && localTtl != null && followerWait.toMillis() > 0
                && localTtl.toMillis() > 0 && followerWait.compareTo(Duration.ofSeconds(30)) <= 0
                && localTtl.compareTo(Duration.ofSeconds(30)) <= 0;
    }
}
