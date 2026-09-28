package me.wly.movie_reservation.common.time;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;

@Configuration(proxyBeanMethods = false)
public class BusinessTimeConfiguration {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock businessClock() {
        return Clock.system(BUSINESS_ZONE);
    }

    @Bean
    public ValidationConfigurationCustomizer businessValidationClock(Clock businessClock) {
        return configuration -> configuration.clockProvider(() -> businessClock);
    }
}
