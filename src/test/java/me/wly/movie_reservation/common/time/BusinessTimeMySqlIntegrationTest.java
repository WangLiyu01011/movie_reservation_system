package me.wly.movie_reservation.common.time;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Creates and removes only its own uniquely named scratch schema. Never drops movie_db. */
@SpringJUnitConfig(BusinessTimeMySqlIntegrationTest.Config.class)
@TestPropertySource(locations = "classpath:application.properties")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class BusinessTimeMySqlIntegrationTest {
    @Value("${spring.datasource.url}") String url;
    @Value("${spring.datasource.username}") String username;
    @Value("${spring.datasource.password}") String password;
    @Value("${spring.datasource.hikari.connection-init-sql}") String initSql;

    @Test
    void freshBaselineAndEveryPoolConnectionUseShanghaiTime() throws Exception {
        assertThat(url).startsWith("jdbc:mysql://127.0.0.1:3306/movie_db?");
        String schema = "movie_clock_verify_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(url, username, password);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            try {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(url.replace("/movie_db?", "/" + schema + "?"));
                config.setUsername(username);
                config.setPassword(password);
                config.setConnectionInitSql(initSql);
                config.setMaximumPoolSize(2);
                try (HikariDataSource dataSource = new HikariDataSource(config)) {
                    Flyway flyway = Flyway.configure().dataSource(dataSource).load();
                    flyway.migrate();
                    flyway.validate();
                    try (Connection first = dataSource.getConnection();
                         Connection second = dataSource.getConnection()) {
                        for (Connection connection : new Connection[]{first, second}) {
                            try (Statement sql = connection.createStatement();
                                 ResultSet result = sql.executeQuery(
                                         "SELECT @@session.time_zone, TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), CURRENT_TIMESTAMP())")) {
                                result.next();
                                assertThat(result.getString(1)).isEqualTo("+08:00");
                                assertThat(result.getInt(2)).isEqualTo(8 * 3600);
                            }
                        }
                        try (Statement sql = first.createStatement();
                             ResultSet history = sql.executeQuery("SELECT script FROM flyway_schema_history WHERE success=1")) {
                            assertThat(history.next()).isTrue();
                            assertThat(history.getString(1)).isEqualTo("B1__init_schema_shanghai.sql");
                            assertThat(history.next()).isTrue();
                            assertThat(history.getString(1)).isEqualTo("V2__ai_chat_sessions.sql");
                            assertThat(history.next()).isFalse();
                        }
                    }
                }
            } finally {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    @Configuration(proxyBeanMethods = false) static class Config {}
}
