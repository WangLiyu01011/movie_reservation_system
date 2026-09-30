package me.wly.movie_reservation.showtime;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.showtime.dto.ShowtimeCandidateDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchDTO;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.Showtime;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import me.wly.movie_reservation.theater.model.Area;
import me.wly.movie_reservation.theater.model.Hall;
import me.wly.movie_reservation.theater.model.HallType;
import me.wly.movie_reservation.theater.model.Seat;
import me.wly.movie_reservation.theater.model.SeatType;
import me.wly.movie_reservation.theater.model.Theater;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses existing MySQL tables; all fixture rows roll back, and schema migration is disabled. */
@DataJpaTest(showSql = false, properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session.events.log=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ShowtimeQueryMySqlIntegrationTest.PersistenceConfiguration.class)
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class ShowtimeQueryMySqlIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 12, 0);
    private static final LocalDateTime FROM = NOW.plusDays(1);

    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private ShowtimeQueryService service;

    private int movieId;
    private int cityId;
    private int districtId;
    private long firstId;
    private long secondId;
    private long otherDistrictShowtimeId;
    private long futureId;

    @BeforeEach
    void insertIsolatedFixtures() {
        int areaBase = ThreadLocalRandom.current().nextInt(1_000_000_000, 2_000_000_000);
        Area city = area(areaBase, "查询测试市", 0, (short) 1);
        Area district = area(areaBase + 1, "查询测试区", city.getId(), (short) 2);
        Area otherDistrict = area(areaBase + 2, "另一测试区", city.getId(), (short) 2);
        Area otherCity = area(areaBase + 3, "另一测试市", 0, (short) 1);
        Area remoteDistrict = area(areaBase + 4, "外地测试区", otherCity.getId(), (short) 2);
        cityId = city.getId();
        districtId = district.getId();

        Movie movie = movie("查询测试电影");
        Movie otherMovie = movie("另一测试电影");
        movieId = movie.getId();
        Hall localHall = hall(theater(city, district), "ACTIVE");
        Hall nearbyHall = hall(theater(city, otherDistrict), "ACTIVE");
        Hall remoteHall = hall(theater(otherCity, remoteDistrict), "ACTIVE");
        Hall closedHall = hall(theater(city, district), "CLOSED");

        // An insufficient early showtime must not consume a slot in the requested page.
        showtime(movie, localHall, FROM, SeatStatus.AVAILABLE, SeatStatus.SOLD, SeatStatus.LOCKED);
        firstId = showtime(movie, localHall, FROM, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE, SeatStatus.SOLD);
        secondId = showtime(movie, localHall, FROM, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE);
        otherDistrictShowtimeId = showtime(movie, nearbyHall, FROM.plusHours(2),
                SeatStatus.AVAILABLE, SeatStatus.AVAILABLE, SeatStatus.SOLD);
        showtime(movie, remoteHall, FROM, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE);
        showtime(otherMovie, localHall, FROM, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE);
        showtime(movie, closedHall, FROM, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE);
        showtime(movie, localHall, FROM.minusSeconds(1), SeatStatus.AVAILABLE, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE);
        showtime(movie, localHall, FROM.plusHours(6), SeatStatus.AVAILABLE, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE);
        showtime(movie, localHall, FROM.plusHours(3), SeatStatus.SOLD, SeatStatus.SOLD, SeatStatus.SOLD);
        showtime(movie, localHall, NOW, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE);
        futureId = showtime(movie, localHall, NOW.plusMinutes(1),
                SeatStatus.AVAILABLE, SeatStatus.AVAILABLE, SeatStatus.AVAILABLE);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void filtersMovieCityTimeHallAndInventoryAndProjectsCurrentAddress() {
        var result = service.searchCandidates(request(null, 0, 10));

        assertThat(result.items()).extracting(ShowtimeCandidateDTO::showtimeId)
                .containsExactly(firstId, secondId, otherDistrictShowtimeId);
        assertThat(result.items()).extracting(ShowtimeCandidateDTO::availableSeatCount)
                .containsExactly(2L, 3L, 2L);
        assertThat(result.items()).allSatisfy(item -> {
            assertThat(item.movieId()).isEqualTo(movieId);
            assertThat(item.imdbId()).isNull();
            assertThat(item.cityName()).isEqualTo("查询测试市");
            assertThat(item.theaterAddress()).isEqualTo("测试影院当前地址");
            assertThat(item.hallType()).isEqualTo(HallType.IMAX);
            assertThat(item.price()).isEqualByComparingTo("45.00");
        });
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void districtFilterAndStablePaginationRunAfterInventoryFiltering() {
        var first = service.searchCandidates(request(districtId, 0, 1));
        var second = service.searchCandidates(request(districtId, 1, 1));

        assertThat(first.items()).extracting(ShowtimeCandidateDTO::showtimeId).containsExactly(firstId);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.items()).extracting(ShowtimeCandidateDTO::showtimeId).containsExactly(secondId);
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void excludesShowtimeStartingExactlyNowEvenWhenWithinRequestedRange() {
        var result = service.searchCandidates(new ShowtimeSearchDTO(movieId, cityId, null,
                NOW.minusMinutes(1), NOW.plusMinutes(2), 2, 0, 10));
        assertThat(result.items()).extracting(ShowtimeCandidateDTO::showtimeId).containsExactly(futureId);
    }

    @Test
    void candidateCountDoesNotIncreaseTheNumberOfSqlQueries() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(service.searchCandidates(request(null, 0, 10)).items()).hasSize(3);
        // Movie existence, city validation, scalar candidate query, and one grouped inventory query.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);

        entityManager.clear();
        statistics.clear();
        assertThat(service.searchCandidates(request(null, 0, 1)).items()).hasSize(1);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
    }

    @Test
    void recordsQueryPlanUsingExistingIndexesWithoutSchemaChanges() throws Exception {
        String sql = """
                EXPLAIN FORMAT=JSON
                SELECT s.id, m.id, m.imdb_id, s.movie_title,
                       t.id, s.theater_name, t.location,
                       c.id, c.name, d.id, d.name,
                       h.id, s.hall_name, h.type, s.start_time, s.end_time, s.price, 0
                FROM showtime s
                JOIN movie m ON m.id = s.movie_id
                JOIN theater t ON t.id = s.theater_id
                JOIN hall h ON h.id = s.hall_id
                JOIN area c ON c.id = t.city_id
                JOIN area d ON d.id = t.district_id
                WHERE m.id = :movieId AND c.id = :cityId
                  AND s.start_time >= :startFrom AND s.start_time < :startTo AND s.start_time > :now
                  AND h.status = 'ACTIVE'
                  AND (SELECT COUNT(*) FROM showtime_seat ss
                       WHERE ss.showtime_id = s.id AND ss.status = 'AVAILABLE') >= 2
                ORDER BY s.start_time, s.id LIMIT 11
                """;
        Object plan = entityManager.createNativeQuery(sql)
                .setParameter("movieId", movieId).setParameter("cityId", cityId)
                .setParameter("startFrom", FROM).setParameter("startTo", FROM.plusHours(6))
                .setParameter("now", NOW).getSingleResult();
        Path output = Path.of("target", "showtime-query-explain.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, plan.toString());
        // MySQL 8.x and newer servers expose different EXPLAIN JSON formats.
        assertThat(plan.toString()).containsAnyOf("\"query_block\"", "\"query_plan\"");
    }

    private ShowtimeSearchDTO request(Integer district, int page, int size) {
        return new ShowtimeSearchDTO(movieId, cityId, district, FROM, FROM.plusHours(6), 2, page, size);
    }

    private Area area(int id, String name, int parent, short level) {
        Area area = new Area(id, name, parent, level);
        entityManager.persist(area);
        return area;
    }

    private Movie movie(String title) {
        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setDescription("测试简介");
        movie.setReleaseDate(NOW.minusDays(1));
        movie.setGenres(List.of("剧情"));
        movie.setLanguage("中文");
        entityManager.persist(movie);
        return movie;
    }

    private Theater theater(Area city, Area district) {
        Theater theater = new Theater();
        theater.setCity(city);
        theater.setDistrict(district);
        theater.setTheaterName("查询测试影院-" + UUID.randomUUID());
        theater.setLocation("测试影院当前地址");
        entityManager.persist(theater);
        return theater;
    }

    private Hall hall(Theater theater, String status) {
        Hall hall = new Hall();
        hall.setTheater(theater);
        hall.setName("测试厅");
        hall.setType(HallType.IMAX);
        hall.setStatus(status);
        hall.setRowCount(1);
        hall.setColumnCount(3);
        entityManager.persist(hall);
        for (int column = 1; column <= 3; column++) {
            Seat seat = new Seat();
            seat.setHall(hall);
            seat.setX(1);
            seat.setY(column);
            seat.setSeatType(SeatType.NORMAL);
            seat.setSeatLabel("A" + column);
            entityManager.persist(seat);
        }
        return hall;
    }

    private long showtime(Movie movie, Hall hall, LocalDateTime start, SeatStatus... statuses) {
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setMovieTitle(movie.getTitle());
        showtime.setTheater(hall.getTheater());
        showtime.setTheaterName(hall.getTheater().getTheaterName());
        showtime.setHall(hall);
        showtime.setHallName(hall.getName());
        showtime.setStartTime(start);
        showtime.setEndTime(start.plusHours(2));
        showtime.setPrice(new BigDecimal("45.00"));
        entityManager.persist(showtime);
        List<Seat> seats = entityManager.createQuery("select s from Seat s where s.hall.id = :hallId order by s.y", Seat.class)
                .setParameter("hallId", hall.getId()).getResultList();
        for (int index = 0; index < statuses.length; index++) {
            ShowtimeSeat seat = new ShowtimeSeat();
            seat.setShowtime(showtime);
            seat.setSeat(seats.get(index));
            seat.setStatus(statuses[index]);
            seat.setSeatTypeSnapshot(SeatType.NORMAL);
            seat.setSeatLabelSnapshot(seats.get(index).getSeatLabel());
            entityManager.persist(seat);
        }
        return showtime.getId();
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan("me.wly.movie_reservation")
    @EnableJpaRepositories("me.wly.movie_reservation")
    @Import({ShowtimeCandidateRepository.class, ShowtimeQueryService.class, ValidationAutoConfiguration.class})
    static class PersistenceConfiguration {
        @Bean
        Clock businessClock() {
            ZoneId zone = ZoneId.of("Asia/Shanghai");
            return Clock.fixed(NOW.atZone(zone).toInstant(), zone);
        }
    }
}
