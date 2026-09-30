package me.wly.movie_reservation.showtime;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.movie.MovieRepository;
import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.showtime.dto.ShowtimeCreateDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeDTO;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.Showtime;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import me.wly.movie_reservation.theater.HallRepository;
import me.wly.movie_reservation.theater.SeatRepository;
import me.wly.movie_reservation.theater.TheaterAdminRepository;
import me.wly.movie_reservation.theater.TheaterRepository;
import me.wly.movie_reservation.theater.model.Hall;
import me.wly.movie_reservation.theater.model.Seat;
import me.wly.movie_reservation.theater.model.SeatType;
import me.wly.movie_reservation.theater.model.Theater;
import me.wly.movie_reservation.user.UserRepository;
import me.wly.movie_reservation.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowtimeServiceTest {
    private static final String ADMIN_USERNAME = "manager";

    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private TheaterRepository theaterRepository;
    @Mock
    private TheaterAdminRepository theaterAdminRepository;
    @Mock
    private HallRepository hallRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private ShowtimeSeatRepository showtimeSeatRepository;
    @Mock
    private ShowtimeMapper showtimeMapper;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private ShowtimeService showtimeService;

    @Test
    void createShowtime_createsAvailableSnapshotSeatsForSellableStaticSeats() {
        Theater theater = theater(1, "西湖影城");
        Hall hall = hall(2, "IMAX 厅", theater);
        Movie movie = movie(3, "tt001", "测试电影");
        ShowtimeCreateDTO dto = validDto();
        authorizeAdminForTheater(1);
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater));
        when(hallRepository.findByIdForUpdate(2)).thenReturn(Optional.of(hall));
        when(showtimeRepository.findFirstByHall_IdAndStartTimeLessThanAndEndTimeGreaterThan(
                2, dto.endTime(), dto.startTime())).thenReturn(Optional.empty());
        when(movieRepository.findMovieByImdbId("tt001")).thenReturn(Optional.of(movie));
        when(seatRepository.findAllByHall_IdOrderByXAscYAsc(2)).thenReturn(List.of(
                seat(11, hall, "A1", SeatType.NORMAL),
                seat(12, hall, "A2", SeatType.EMPTY),
                seat(13, hall, "A3", SeatType.VIP)
        ));
        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(invocation -> {
            Showtime showtime = invocation.getArgument(0);
            showtime.setId(1001L);
            return showtime;
        });
        when(showtimeMapper.entityToDTO(any(Showtime.class))).thenAnswer(invocation -> {
            Showtime showtime = invocation.getArgument(0);
            return new ShowtimeDTO(showtime.getId(), showtime.getStartTime(), showtime.getEndTime(),
                    showtime.getTheaterName(), showtime.getHallName(), showtime.getMovieTitle(), showtime.getPrice());
        });

        ShowtimeDTO result = showtimeService.createShowtime(dto, ADMIN_USERNAME);

        var lockOrder = inOrder(hallRepository, showtimeRepository, showtimeSeatRepository);
        lockOrder.verify(hallRepository).findByIdForUpdate(2);
        lockOrder.verify(showtimeRepository).findFirstByHall_IdAndStartTimeLessThanAndEndTimeGreaterThan(
                2, dto.endTime(), dto.startTime());
        lockOrder.verify(showtimeRepository).save(any(Showtime.class));
        lockOrder.verify(showtimeSeatRepository).saveAll(anyList());
        verify(hallRepository, never()).findById(any());

        ArgumentCaptor<List<ShowtimeSeat>> seatsCaptor = listCaptor();
        verify(showtimeSeatRepository).saveAll(seatsCaptor.capture());
        List<ShowtimeSeat> snapshots = seatsCaptor.getValue();
        assertEquals(2, snapshots.size());
        assertEquals(List.of("A1", "A3"), snapshots.stream().map(ShowtimeSeat::getSeatLabelSnapshot).toList());
        assertEquals(List.of(SeatType.NORMAL, SeatType.VIP), snapshots.stream().map(ShowtimeSeat::getSeatTypeSnapshot).toList());
        assertEquals(List.of(SeatStatus.AVAILABLE, SeatStatus.AVAILABLE), snapshots.stream().map(ShowtimeSeat::getStatus).toList());
        assertEquals(1001L, result.id());
        assertEquals("西湖影城", result.theaterName());
        assertEquals("IMAX 厅", result.hallName());
    }

    @Test
    void createShowtime_rejectsEndTimeBeforeStartTimeWithoutAccessingShowtimeRepositories() {
        LocalDateTime start = LocalDateTime.of(2030, 1, 1, 12, 0);
        ShowtimeCreateDTO invalidTime = new ShowtimeCreateDTO(
                start, start.minusMinutes(1), 1, 2, "tt001", new BigDecimal("50.00")
        );
        authorizeAdminForTheater(1);

        assertThrows(BusinessException.class,
                () -> showtimeService.createShowtime(invalidTime, ADMIN_USERNAME));

        verifyNoInteractions(showtimeRepository, theaterRepository, hallRepository, movieRepository,
                seatRepository, showtimeSeatRepository, showtimeMapper);
    }

    @Test
    void createShowtime_rejectsHallFromAnotherTheater() {
        Theater requestedTheater = theater(1, "西湖影城");
        Hall otherTheaterHall = hall(2, "二号厅", theater(9, "滨江影城"));
        authorizeAdminForTheater(1);
        when(theaterRepository.findById(1)).thenReturn(Optional.of(requestedTheater));
        when(hallRepository.findByIdForUpdate(2)).thenReturn(Optional.of(otherTheaterHall));

        assertThrows(BusinessException.class,
                () -> showtimeService.createShowtime(validDto(), ADMIN_USERNAME));

        verify(showtimeRepository, never()).save(any());
        verify(showtimeSeatRepository, never()).saveAll(anyList());
    }

    @Test
    void createShowtime_rejectsOverlappingShowtimeBeforeCreatingSeats() {
        Theater theater = theater(1, "西湖影城");
        Hall hall = hall(2, "IMAX 厅", theater);
        ShowtimeCreateDTO dto = validDto();
        authorizeAdminForTheater(1);
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater));
        when(hallRepository.findByIdForUpdate(2)).thenReturn(Optional.of(hall));
        when(showtimeRepository.findFirstByHall_IdAndStartTimeLessThanAndEndTimeGreaterThan(
                2, dto.endTime(), dto.startTime())).thenReturn(Optional.of(new Showtime()));

        assertThrows(BusinessException.class,
                () -> showtimeService.createShowtime(dto, ADMIN_USERNAME));

        var lockOrder = inOrder(hallRepository, showtimeRepository);
        lockOrder.verify(hallRepository).findByIdForUpdate(2);
        lockOrder.verify(showtimeRepository).findFirstByHall_IdAndStartTimeLessThanAndEndTimeGreaterThan(
                2, dto.endTime(), dto.startTime());

        verify(movieRepository, never()).findMovieByImdbId(any());
        verify(seatRepository, never()).findAllByHall_IdOrderByXAscYAsc(any());
        verify(showtimeRepository, never()).save(any());
    }

    @Test
    void createShowtime_rejectsMissingHallBeforeCheckingIntervals() {
        authorizeAdminForTheater(1);
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater(1, "西湖影城")));
        when(hallRepository.findByIdForUpdate(2)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> showtimeService.createShowtime(validDto(), ADMIN_USERNAME));

        verifyNoInteractions(showtimeRepository, movieRepository, seatRepository, showtimeSeatRepository);
    }

    @Test
    void createShowtime_rejectsAdminOfAnotherTheaterBeforeAccessingShowtimeRepositories() {
        User admin = user(10L, ADMIN_USERNAME);
        when(userRepository.findByUsername(ADMIN_USERNAME)).thenReturn(Optional.of(admin));
        when(theaterAdminRepository.existsByUser_IdAndTheater_Id(admin.getId(), 1)).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> showtimeService.createShowtime(validDto(), ADMIN_USERNAME));

        verifyNoInteractions(showtimeRepository, theaterRepository, hallRepository, movieRepository,
                seatRepository, showtimeSeatRepository, showtimeMapper);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<ShowtimeSeat>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private ShowtimeCreateDTO validDto() {
        return new ShowtimeCreateDTO(
                LocalDateTime.of(2030, 1, 1, 12, 0),
                LocalDateTime.of(2030, 1, 1, 14, 0),
                1, 2, "tt001", new BigDecimal("50.00")
        );
    }

    private void authorizeAdminForTheater(int theaterId) {
        User admin = user(10L, ADMIN_USERNAME);
        when(userRepository.findByUsername(ADMIN_USERNAME)).thenReturn(Optional.of(admin));
        when(theaterAdminRepository.existsByUser_IdAndTheater_Id(admin.getId(), theaterId)).thenReturn(true);
    }

    private User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private Theater theater(int id, String name) {
        Theater theater = new Theater();
        theater.setId(id);
        theater.setTheaterName(name);
        return theater;
    }

    private Hall hall(int id, String name, Theater theater) {
        Hall hall = new Hall();
        hall.setId(id);
        hall.setName(name);
        hall.setTheater(theater);
        return hall;
    }

    private Movie movie(int id, String imdbId, String title) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setImdbId(imdbId);
        movie.setTitle(title);
        return movie;
    }

    private Seat seat(long id, Hall hall, String label, SeatType type) {
        Seat seat = new Seat();
        seat.setId(id);
        seat.setHall(hall);
        seat.setSeatLabel(label);
        seat.setSeatType(type);
        return seat;
    }
}
