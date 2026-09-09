package me.wly.movie_reservation.theater;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.theater.dto.HallCreateDTO;
import me.wly.movie_reservation.theater.dto.HallDTO;
import me.wly.movie_reservation.theater.dto.HallUpdateDTO;
import me.wly.movie_reservation.theater.dto.SeatCellCreateDTO;
import me.wly.movie_reservation.theater.model.Hall;
import me.wly.movie_reservation.theater.model.HallType;
import me.wly.movie_reservation.theater.model.Seat;
import me.wly.movie_reservation.theater.model.SeatType;
import me.wly.movie_reservation.theater.model.Theater;
import me.wly.movie_reservation.user.UserRepository;
import me.wly.movie_reservation.user.model.User;
import me.wly.movie_reservation.user.model.UserRole;
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
    @Mock
    private UserRepository userRepository;
    @Mock
    private TheaterAdminRepository theaterAdminRepository;
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
        allowTheaterManagement("manager", theater);
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

        HallDTO result = hallService.createHall(dto, "manager");

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
        Theater theater = theater(1);
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater));
        allowTheaterManagement("manager", theater);
        when(hallRepository.existsByTheater_IdAndName(1, "一号厅")).thenReturn(false);
        HallCreateDTO incompleteLayout = new HallCreateDTO(
                1, "一号厅", HallType.BASIC, 2, 2,
                List.of(cell(1, 1, SeatType.NORMAL), cell(1, 2, SeatType.NORMAL), cell(2, 1, SeatType.NORMAL))
        );

        assertThrows(BusinessException.class, () -> hallService.createHall(incompleteLayout, "manager"));

        verify(hallRepository, never()).save(any());
        verify(seatRepository, never()).saveAll(anyList());
    }

    @Test
    void createHall_rejectsDuplicateHallNameInTheater() {
        Theater theater = theater(1);
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater));
        allowTheaterManagement("manager", theater);
        when(hallRepository.existsByTheater_IdAndName(1, "一号厅")).thenReturn(true);

        assertThrows(BusinessException.class, () -> hallService.createHall(validDto(), "manager"));

        verify(hallRepository, never()).save(any());
    }

    @Test
    void createHall_rejectsTheaterAdminWhoDoesNotManageTargetTheater() {
        Theater theater = theater(1);
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater));
        User otherTheaterAdmin = user(10L, UserRole.THEATER_ADMIN);
        when(userRepository.findByUsername("other-manager")).thenReturn(Optional.of(otherTheaterAdmin));
        when(theaterAdminRepository.existsByUser_IdAndTheater_Id(10L, 1)).thenReturn(false);

        assertThrows(BusinessException.class, () -> hallService.createHall(validDto(), "other-manager"));

        verify(hallRepository, never()).save(any());
    }

    @Test
    void updateHall_allowsAssignedAdminToChangeHallMetadata() {
        Theater theater = theater(1);
        Hall hall = new Hall();
        hall.setId(301);
        hall.setTheater(theater);
        hall.setName("旧名称");
        hall.setType(HallType.BASIC);
        hall.setStatus("ACTIVE");
        hall.setRowCount(1);
        hall.setColumnCount(1);
        when(hallRepository.findById(301)).thenReturn(Optional.of(hall));
        allowTheaterManagement("manager", theater);
        when(hallRepository.existsByTheater_IdAndNameAndIdNot(1, "新名称", 301)).thenReturn(false);
        when(hallRepository.save(hall)).thenReturn(hall);
        when(seatRepository.findAllByHall_IdOrderByXAscYAsc(301)).thenReturn(List.of());

        HallDTO result = hallService.updateHall(301, new HallUpdateDTO("新名称", HallType.IMAX, "INACTIVE"), "manager");

        assertEquals("新名称", result.name());
        assertEquals(HallType.IMAX, result.type());
        assertEquals("INACTIVE", result.status());
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

    private void allowTheaterManagement(String username, Theater theater) {
        User user = user(10L, UserRole.THEATER_ADMIN);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(theaterAdminRepository.existsByUser_IdAndTheater_Id(user.getId(), theater.getId())).thenReturn(true);
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        return user;
    }
}
