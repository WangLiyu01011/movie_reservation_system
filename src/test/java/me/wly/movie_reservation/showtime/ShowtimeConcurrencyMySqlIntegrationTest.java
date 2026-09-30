package me.wly.movie_reservation.showtime;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.showtime.dto.ShowtimeCreateDTO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Real service + JPA locks, isolated in a disposable schema; never writes business tables in movie_db. */
@SpringJUnitConfig(ShowtimeConcurrencyMySqlIntegrationTest.PropertyConfiguration.class)
@TestPropertySource(locations = "classpath:application.properties")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShowtimeConcurrencyMySqlIntegrationTest {
    @Value("${spring.datasource.url}") private String url;
    @Value("${spring.datasource.username}") private String username;
    @Value("${spring.datasource.password}") private String password;

    private final String schema = "movie_schedule_test_" + UUID.randomUUID().toString().replace("-", "");
    private boolean schemaCreated;
    private HikariDataSource dataSource;
    private AnnotationConfigApplicationContext context;
    private ShowtimeService service;
    private TransactionTemplate transaction;
    private JdbcTemplate jdbc;
    private static final LocalDateTime START = LocalDateTime.of(2035, 1, 1, 12, 0);

    @BeforeAll
    void createIsolatedDatabase() throws Exception {
        // Deliberately fail for unexpected URLs instead of accidentally testing against a business schema.
        assertThat(url).startsWith("jdbc:mysql://127.0.0.1:3306/movie_db?");
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            schemaCreated = true;
        }
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(url.replace("/movie_db?", "/" + schema + "?"));
        pool.setUsername(username);
        pool.setPassword(password);
        pool.setMaximumPoolSize(4);
        pool.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
        pool.setConnectionInitSql("SET SESSION time_zone = '+08:00', innodb_lock_wait_timeout = 5");
        dataSource = new HikariDataSource(pool);
        Flyway.configure().dataSource(dataSource).load().migrate();

        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(ShowtimeMapper.class, () -> Mappers.getMapper(ShowtimeMapper.class));
        context.register(PersistenceConfiguration.class, ShowtimeService.class);
        context.refresh();
        service = context.getBean(ShowtimeService.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        jdbc = new JdbcTemplate(dataSource);

        jdbc.update("INSERT INTO area (id, name, parent_id, level) VALUES (900000001, '测试市', 0, 1), (900000002, '测试区', 900000001, 2)");
        jdbc.update("INSERT INTO theater (id, theater_name, location, city_id, district_id) VALUES (1, '并发测试影院', '测试地址', 900000001, 900000002)");
        jdbc.update("INSERT INTO hall (id, theater_id, type, status, name, row_count, column_count) VALUES (1, 1, 'IMAX', 'ACTIVE', '一号厅', 1, 1), (2, 1, 'IMAX', 'ACTIVE', '二号厅', 1, 1)");
        jdbc.update("INSERT INTO seat (hall_id, x, y, seat_type, seat_label) VALUES (1, 1, 1, 'NORMAL', 'A1'), (2, 1, 1, 'NORMAL', 'A1')");
        jdbc.update("INSERT INTO movie (id, imdb_id, title) VALUES (1, 'tt_lock', '并发测试电影')");
        jdbc.update("INSERT INTO users (id, code, username, nickname, password, email_address, user_role) VALUES (1, 'usr_lock', 'schedule_test', '测试管理员', 'unused', 'schedule@test.invalid', 'THEATER_ADMIN')");
        jdbc.update("INSERT INTO theater_admin (theater_id, user_id) VALUES (1, 1)");
    }

    @AfterAll
    void removeOnlyIsolatedDatabase() throws Exception {
        if (context != null) context.close();
        if (dataSource != null) dataSource.close();
        if (schemaCreated) {
            assertThat(schema).matches("movie_schedule_test_[a-f0-9]{32}");
            try (var connection = DriverManager.getConnection(url, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    @BeforeEach
    void clearIsolatedSchedules() {
        jdbc.update("DELETE FROM showtime_seat");
        jdbc.update("DELETE FROM showtime");
    }

    @Test
    void overlappingRequestWaitsThenRejectsEvenWithAnOlderRepeatableReadSnapshot() throws Exception {
        runConcurrentScenario(request(1, START), true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM showtime", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM showtime_seat", Long.class)).isEqualTo(1);
    }

    @Test
    void adjacentRequestWaitsThenSucceeds() throws Exception {
        runConcurrentScenario(request(1, START.plusHours(2)), false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM showtime", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM showtime_seat", Long.class)).isEqualTo(2);
    }

    private void runConcurrentScenario(ShowtimeCreateDTO secondRequest, boolean shouldReject) throws Exception {
        var firstSaved = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        var snapshotTaken = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> transaction.execute(status -> {
                var created = service.createShowtime(request(1, START), "schedule_test");
                firstSaved.countDown();
                await(allowCommit);
                return created;
            }));
            await(firstSaved);
            Future<?> second = workers.submit(() -> transaction.execute(status -> {
                // Establish a snapshot BEFORE the first request commits. A plain exists query would stay stale.
                assertThat(jdbc.queryForObject("SELECT count(*) FROM showtime", Long.class)).isZero();
                snapshotTaken.countDown();
                return service.createShowtime(secondRequest, "schedule_test");
            }));
            await(snapshotTaken);
            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            allowCommit.countDown();
            first.get(10, TimeUnit.SECONDS);
            if (shouldReject) {
                var error = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
                assertThat(error.getCause()).isInstanceOf(BusinessException.class)
                        .hasMessage("Showtime overlaps an existing showtime in this hall");
            } else {
                assertThat(second.get(10, TimeUnit.SECONDS)).isNotNull();
            }
        } finally {
            allowCommit.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void differentHallCanBeScheduledWhileFirstHallIsLocked() throws Exception {
        var hallLocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = worker.submit(() -> transaction.execute(status -> {
                context.getBean(me.wly.movie_reservation.theater.HallRepository.class).findByIdForUpdate(1);
                hallLocked.countDown();
                await(release);
                return null;
            }));
            await(hallLocked);
            var created = service.createShowtime(request(2, START), "schedule_test");
            assertThat(created.id()).isNotNull();
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertThat(worker.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ShowtimeCreateDTO request(int hallId, LocalDateTime start) {
        return new ShowtimeCreateDTO(start, start.plusHours(2), 1, hallId, "tt_lock", new BigDecimal("50.00"));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("Concurrent transaction reached checkpoint").isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PropertyConfiguration { }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories("me.wly.movie_reservation")
    static class PersistenceConfiguration {
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("me.wly.movie_reservation");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "validate",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
