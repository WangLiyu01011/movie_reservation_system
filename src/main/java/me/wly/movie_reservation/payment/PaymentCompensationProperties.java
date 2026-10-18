package me.wly.movie_reservation.payment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component @ConfigurationProperties(prefix = "payment.compensation") @Validated
@Getter @Setter
public class PaymentCompensationProperties {
    @Min(1) private int batchSize = 20;
    @Min(1) private int maxAttempts = 8;
    @NotNull private Duration lease = Duration.ofSeconds(30);
    @NotNull private Duration pollInterval = Duration.ofSeconds(5);

    @AssertTrue(message = "lease and poll interval must be positive")
    public boolean isTimingValid() {
        return lease != null && !lease.isNegative() && !lease.isZero()
                && pollInterval != null && !pollInterval.isNegative() && !pollInterval.isZero();
    }
}
