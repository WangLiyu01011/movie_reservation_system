package me.wly.movie_reservation.service;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.model.dto.HallCreateDTO;
import me.wly.movie_reservation.model.dto.HallDTO;
import me.wly.movie_reservation.model.dto.SeatCellCreateDTO;
import me.wly.movie_reservation.model.entity.Hall;
import me.wly.movie_reservation.model.entity.Seat;
import me.wly.movie_reservation.model.entity.Theater;
import me.wly.movie_reservation.model.enum_class.HallType;
import me.wly.movie_reservation.model.enum_class.SeatType;
import me.wly.movie_reservation.repository.HallRepository;
import me.wly.movie_reservation.repository.SeatRepository;
import me.wly.movie_reservation.repository.TheaterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HallServiceTest {
    @Mock
    private HallRepository hallRepository;
    @Mock
    private TheaterRepository theaterRepository;
    @Mock
    private SeatRepository seatRepository;
    @InjectMocks
    private HallService hallService;

    @Test
    void createHall_createsEveryGridCellAndExcelStyleSeatLabels() {
        Theater theater = theater(1);
        HallCreateDTO dto = new HallCreateDTO(
                1, "一号厅", HallType.IMAX, 2, 3,
                List.of(
                        cell(1, 1, SeatType.NORMAL), cell(1, 2, SeatType.VIP), cell(1, 3, SeatType.EMPTY),
                        cell(2, 1, SeatType.NORMAL), cell(2, 2, SeatType.NORMAL), cell(2, 3, SeatType.NORMAL)
                )
        );
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater));
        when(hallRepository.existsByTheater_IdAndName(1, "一号厅")).thenReturn(false);
        when(hallRepository.save(any(Hall.class))).thenAnswer(invocation -> {
            Hall hall = invocation.getArgument(0);
            hall.setId(301);
            return hall;
        });
        when(seatRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Seat> seats = invocation.getArgument(0);
            for (int index = 0; index < seats.size(); index++) {
                seats.get(index).setId((long) index + 1);
            }
            return seats;
        });

        HallDTO result = hallService.createHall(dto);

        ArgumentCaptor<List<Seat>> seatsCaptor = listCaptor();
        verify(seatRepository).saveAll(seatsCaptor.capture());
        List<Seat> savedSeats = seatsCaptor.getValue();
        assertEquals(6, savedSeats.size());
        assertEquals(List.of("A1", "A2", "A3", "B1", "B2", "B3"),
                savedSeats.stream().map(Seat::getSeatLabel).toList());
        assertEquals(SeatType.EMPTY, savedSeats.get(2).getSeatType());
        assertEquals(301, result.id());
        assertEquals(6, result.seats().size());
    }

    @Test
    void createHall_rejectsLayoutThatDoesNotCoverEveryCell() {
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater(1)));
        when(hallRepository.existsByTheater_IdAndName(1, "一号厅")).thenReturn(false);
        HallCreateDTO incompleteLayout = new HallCreateDTO(
                1, "一号厅", HallType.BASIC, 2, 2,
                List.of(cell(1, 1, SeatType.NORMAL), cell(1, 2, SeatType.NORMAL), cell(2, 1, SeatType.NORMAL))
        );

        assertThrows(BusinessException.class, () -> hallService.createHall(incompleteLayout));

        verify(hallRepository, never()).save(any());
        verify(seatRepository, never()).saveAll(anyList());
    }

    @Test
    void createHall_rejectsDuplicateHallNameInTheater() {
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater(1)));
        when(hallRepository.existsByTheater_IdAndName(1, "一号厅")).thenReturn(true);

        assertThrows(BusinessException.class, () -> hallService.createHall(validDto()));

        verify(hallRepository, never()).save(any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<Seat>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private HallCreateDTO validDto() {
        return new HallCreateDTO(
                1, "一号厅", HallType.BASIC, 1, 1, List.of(cell(1, 1, SeatType.NORMAL))
        );
    }

    private SeatCellCreateDTO cell(int row, int column, SeatType seatType) {
        return new SeatCellCreateDTO(row, column, seatType);
    }

    private Theater theater(int id) {
        Theater theater = new Theater();
        theater.setId(id);
        return theater;
    }
}
