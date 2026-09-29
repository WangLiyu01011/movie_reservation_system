package me.wly.movie_reservation.common.time;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Future;
import me.wly.movie_reservation.common.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessTimeConfigurationTest {
    private static final Clock FIXED = Clock.fixed(Instant.parse("2030-01-01T04:00:00Z"),
            BusinessTimeConfiguration.BUSINESS_ZONE);

    @Test
    void beanValidationUsesBusinessClock() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(BusinessTimeConfiguration.class, FixedClockConfiguration.class)
                .run(context -> {
                    Validator validator = context.getBean(Validator.class);
                    assertThat(validator.validate(new ScheduledTime(LocalDateTime.of(2030, 1, 1, 9, 0))))
                            .hasSize(1);
                    assertThat(validator.validate(new ScheduledTime(LocalDateTime.of(2030, 1, 1, 12, 0))))
                            .hasSize(1);
                    assertThat(validator.validate(new ScheduledTime(LocalDateTime.of(2030, 1, 1, 12, 1))))
                            .isEmpty();
                    assertThat(context.getBean("businessClock", Clock.class).getZone())
                            .isEqualTo(ZoneId.of("Asia/Shanghai"));
                });
    }

    @Test
    void jwtGenerationAndExpirationUseSameClock() {
        JwtUtil jwt = jwt(FIXED);
        var token = jwt.generateToken("customer");
        assertThat(token.expireTime()).isEqualTo(FIXED.millis() + 60_000);
        assertThat(jwt.isTokenValid(token.token())).isTrue();
        assertThat(jwt(Clock.offset(FIXED, Duration.ofSeconds(61))).isTokenValid(token.token())).isFalse();
    }

    private JwtUtil jwt(Clock clock) {
        JwtUtil jwt = new JwtUtil(clock);
        ReflectionTestUtils.setField(jwt, "secret", "test-secret-long-enough-for-hmac-sha-256-1234567890");
        ReflectionTestUtils.setField(jwt, "expirationMs", 60_000L);
        jwt.init();
        return jwt;
    }

    record ScheduledTime(@Future LocalDateTime start) {}
    @Configuration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedClock() { return FIXED; }
    }
}
