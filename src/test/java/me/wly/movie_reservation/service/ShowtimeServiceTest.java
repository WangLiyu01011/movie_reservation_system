package me.wly.movie_reservation.service;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.mapper.ShowtimeMapper;
import me.wly.movie_reservation.model.dto.ShowtimeCreateDTO;
import me.wly.movie_reservation.model.dto.ShowtimeDTO;
import me.wly.movie_reservation.model.entity.Hall;
import me.wly.movie_reservation.model.entity.Movie;
import me.wly.movie_reservation.model.entity.Seat;
import me.wly.movie_reservation.model.entity.Showtime;
import me.wly.movie_reservation.model.entity.ShowtimeSeat;
import me.wly.movie_reservation.model.entity.Theater;
import me.wly.movie_reservation.model.enum_class.SeatStatus;
import me.wly.movie_reservation.model.enum_class.SeatType;
import me.wly.movie_reservation.repository.HallRepository;
import me.wly.movie_reservation.repository.MovieRepository;
import me.wly.movie_reservation.repository.SeatRepository;
import me.wly.movie_reservation.repository.ShowtimeRepository;
import me.wly.movie_reservation.repository.ShowtimeSeatRepository;
import me.wly.movie_reservation.repository.TheaterRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowtimeServiceTest {
    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private TheaterRepository theaterRepository;
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
    @InjectMocks
    private ShowtimeService showtimeService;

    @Test
    void createShowtime_createsAvailableSnapshotSeatsForSellableStaticSeats() {
        Theater theater = theater(1, "西湖影城");
        Hall hall = hall(2, "IMAX 厅", theater);
        Movie movie = movie(3, "tt001", "测试电影");
        ShowtimeCreateDTO dto = validDto();
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater));
        when(hallRepository.findById(2)).thenReturn(Optional.of(hall));
        when(showtimeRepository.existsByHall_IdAndStartTimeLessThanAndEndTimeGreaterThan(
                2, dto.endTime(), dto.startTime())).thenReturn(false);
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

        ShowtimeDTO result = showtimeService.createShowtime(dto);

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
    void createShowtime_rejectsEndTimeBeforeStartTimeWithoutAccessingRepositories() {
        LocalDateTime start = LocalDateTime.of(2030, 1, 1, 12, 0);
        ShowtimeCreateDTO invalidTime = new ShowtimeCreateDTO(
                start, start.minusMinutes(1), 1, 2, "tt001", new BigDecimal("50.00")
        );

        assertThrows(BusinessException.class, () -> showtimeService.createShowtime(invalidTime));

        verifyNoInteractions(showtimeRepository, theaterRepository, hallRepository, movieRepository,
                seatRepository, showtimeSeatRepository, showtimeMapper);
    }

    @Test
    void createShowtime_rejectsHallFromAnotherTheater() {
        Theater requestedTheater = theater(1, "西湖影城");
        Hall otherTheaterHall = hall(2, "二号厅", theater(9, "滨江影城"));
        when(theaterRepository.findById(1)).thenReturn(Optional.of(requestedTheater));
        when(hallRepository.findById(2)).thenReturn(Optional.of(otherTheaterHall));

        assertThrows(BusinessException.class, () -> showtimeService.createShowtime(validDto()));

        verify(showtimeRepository, never()).save(any());
        verify(showtimeSeatRepository, never()).saveAll(anyList());
    }

    @Test
    void createShowtime_rejectsOverlappingShowtimeBeforeCreatingSeats() {
        Theater theater = theater(1, "西湖影城");
        Hall hall = hall(2, "IMAX 厅", theater);
        ShowtimeCreateDTO dto = validDto();
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater));
        when(hallRepository.findById(2)).thenReturn(Optional.of(hall));
        when(showtimeRepository.existsByHall_IdAndStartTimeLessThanAndEndTimeGreaterThan(
                2, dto.endTime(), dto.startTime())).thenReturn(true);

        assertThrows(BusinessException.class, () -> showtimeService.createShowtime(dto));

        verify(movieRepository, never()).findMovieByImdbId(any());
        verify(seatRepository, never()).findAllByHall_IdOrderByXAscYAsc(any());
        verify(showtimeRepository, never()).save(any());
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
