package me.wly.movie_reservation.showtime;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.movie.MovieRepository;
import me.wly.movie_reservation.showtime.dto.ShowtimeCandidateDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSeatCountDTO;
import me.wly.movie_reservation.theater.AreaRepository;
import me.wly.movie_reservation.theater.model.Area;
import me.wly.movie_reservation.theater.model.HallType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowtimeQueryServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 11, 0);
    private static final LocalDateTime FROM = NOW.plusDays(1).withHour(12);
    private static ValidatorFactory validatorFactory;

    @Mock private ShowtimeCandidateRepository candidateRepository;
    @Mock private ShowtimeSeatRepository seatRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private AreaRepository areaRepository;
    private ShowtimeQueryService service;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
                ZoneId.of("Asia/Shanghai"));
        service = new ShowtimeQueryService(candidateRepository, seatRepository, movieRepository,
                areaRepository, validatorFactory.getValidator(), clock);
    }

    @Test
    void fillsSeatCountsInOneBatchAndExcludesTheLookaheadRow() {
        ShowtimeSearchDTO request = request(null, 1);
        validMovieAndCity();
        when(candidateRepository.findCandidates(request, NOW))
                .thenReturn(List.of(candidate(1001L), candidate(1002L)));
        when(seatRepository.countAvailableByShowtimeIds(List.of(1001L)))
                .thenReturn(List.of(new ShowtimeSeatCountDTO(1001L, 3)));

        var result = service.searchCandidates(request);

        assertThat(result.hasMore()).isTrue();
        assertThat(result.items()).extracting(ShowtimeCandidateDTO::showtimeId).containsExactly(1001L);
        assertThat(result.items().getFirst().availableSeatCount()).isEqualTo(3);
        verify(seatRepository).countAvailableByShowtimeIds(List.of(1001L));
    }

    @Test
    void missingMatchingShowtimesReturnsEmptyWithoutAnInventoryQuery() {
        ShowtimeSearchDTO request = request(null, 10);
        validMovieAndCity();
        when(candidateRepository.findCandidates(request, NOW)).thenReturn(List.of());

        var result = service.searchCandidates(request);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasMore()).isFalse();
        verifyNoInteractions(seatRepository);
    }

    @Test
    void entirelyPastRangeReturnsEmptyWithoutSearchingCandidates() {
        validMovieAndCity();
        var result = service.searchCandidates(new ShowtimeSearchDTO(12, 100, null,
                NOW.minusDays(1), NOW, null, null, null));

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(candidateRepository, seatRepository);
    }

    @Test
    void normalizesOptionalParameters() {
        var request = new ShowtimeSearchDTO(12, 100, null, FROM, FROM.plusHours(6), null, null, null);
        assertThat(request.minAvailableSeats()).isEqualTo(1);
        assertThat(request.page()).isZero();
        assertThat(request.size()).isEqualTo(10);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidParametersBeforeQueryingTheDatabase(ShowtimeSearchDTO request) {
        assertThatThrownBy(() -> service.searchCandidates(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).resultCode).isEqualTo(ResultCode.BAD_REQUEST));
        verifyNoInteractions(candidateRepository, seatRepository, movieRepository, areaRepository);
    }

    static Stream<ShowtimeSearchDTO> invalidRequests() {
        return Stream.of(
                null,
                new ShowtimeSearchDTO(null, 100, null, FROM, FROM.plusHours(6), 1, 0, 10),
                new ShowtimeSearchDTO(0, 100, null, FROM, FROM.plusHours(6), 1, 0, 10),
                new ShowtimeSearchDTO(12, null, null, FROM, FROM.plusHours(6), 1, 0, 10),
                new ShowtimeSearchDTO(12, 0, null, FROM, FROM.plusHours(6), 1, 0, 10),
                new ShowtimeSearchDTO(12, 100, 0, FROM, FROM.plusHours(6), 1, 0, 10),
                new ShowtimeSearchDTO(12, 100, null, null, FROM.plusHours(6), 1, 0, 10),
                new ShowtimeSearchDTO(12, 100, null, FROM, null, 1, 0, 10),
                new ShowtimeSearchDTO(12, 100, null, FROM, FROM, 1, 0, 10),
                new ShowtimeSearchDTO(12, 100, null, FROM, FROM.minusHours(1), 1, 0, 10),
                new ShowtimeSearchDTO(12, 100, null, FROM, FROM.plusDays(31).plusSeconds(1), 1, 0, 10),
                new ShowtimeSearchDTO(12, 100, null, FROM, FROM.plusHours(6), 0, 0, 10),
                new ShowtimeSearchDTO(12, 100, null, FROM, FROM.plusHours(6), 1, -1, 10),
                new ShowtimeSearchDTO(12, 100, null, FROM, FROM.plusHours(6), 1, 0, 0),
                new ShowtimeSearchDTO(12, 100, null, FROM, FROM.plusHours(6), 1, 0, 21),
                new ShowtimeSearchDTO(12, 100, null, FROM, FROM.plusHours(6), 1, Integer.MAX_VALUE, 20));
    }

    @Test
    void rejectsUnknownMovie() {
        assertThatThrownBy(() -> service.searchCandidates(request(null, 10)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).resultCode).isEqualTo(ResultCode.MOVIE_NOT_FOUND));
        verifyNoInteractions(areaRepository, candidateRepository, seatRepository);
    }

    @Test
    void rejectsUnknownCity() {
        when(movieRepository.existsById(12)).thenReturn(true);
        assertThatThrownBy(() -> service.searchCandidates(request(null, 10)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("City not found");
        verifyNoInteractions(candidateRepository, seatRepository);
    }

    @Test
    void rejectsAnAreaThatIsNotACity() {
        when(movieRepository.existsById(12)).thenReturn(true);
        when(areaRepository.findById(100)).thenReturn(Optional.of(new Area(100, "省", 0, (short) 0)));
        assertThatThrownBy(() -> service.searchCandidates(request(null, 10)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("must refer to a city");
        verifyNoInteractions(candidateRepository, seatRepository);
    }

    @Test
    void rejectsDistrictFromAnotherCity() {
        validMovieAndCity();
        when(areaRepository.findById(101)).thenReturn(Optional.of(new Area(101, "其他区", 200, (short) 2)));
        assertThatThrownBy(() -> service.searchCandidates(request(101, 10)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("belong to the selected city");
        verifyNoInteractions(candidateRepository, seatRepository);
    }

    @Test
    void rejectsWrongDistrictLevel() {
        validMovieAndCity();
        when(areaRepository.findById(101)).thenReturn(Optional.of(new Area(101, "其他城市", 100, (short) 1)));
        assertThatThrownBy(() -> service.searchCandidates(request(101, 10)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("belong to the selected city");
    }

    @Test
    void rejectsUnknownDistrict() {
        validMovieAndCity();
        assertThatThrownBy(() -> service.searchCandidates(request(101, 10)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("District not found");
    }

    private ShowtimeSearchDTO request(Integer districtId, int size) {
        return new ShowtimeSearchDTO(12, 100, districtId, FROM, FROM.plusHours(6), 2, 0, size);
    }

    private void validMovieAndCity() {
        when(movieRepository.existsById(12)).thenReturn(true);
        when(areaRepository.findById(100)).thenReturn(Optional.of(new Area(100, "杭州市", 0, (short) 1)));
    }

    private ShowtimeCandidateDTO candidate(long id) {
        return new ShowtimeCandidateDTO(id, 12, null, "测试电影", 1, "影院", "地址",
                100, "杭州市", 101, "西湖区", 2, "3号厅", HallType.IMAX,
                FROM, FROM.plusHours(2), new BigDecimal("45.00"), 0);
    }
}
