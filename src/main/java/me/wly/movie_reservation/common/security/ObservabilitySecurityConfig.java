package me.wly.movie_reservation.common.security;

import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** The observability profile binds the management server to a separate loopback-only socket. */
@Configuration(proxyBeanMethods = false)
@Profile("observability")
public class ObservabilitySecurityConfig {
    @Bean
    @Order(0)
    SecurityFilterChain managementSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/actuator/**").authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus").permitAll()
                .anyRequest().denyAll()).build();
    }
}
