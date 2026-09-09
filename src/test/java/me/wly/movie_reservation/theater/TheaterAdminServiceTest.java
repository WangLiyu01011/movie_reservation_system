package me.wly.movie_reservation.theater;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.theater.dto.TheaterAdminAssignDTO;
import me.wly.movie_reservation.theater.model.Theater;
import me.wly.movie_reservation.theater.model.TheaterAdmin;
import me.wly.movie_reservation.theater.vo.TheaterAdminVO;
import me.wly.movie_reservation.user.UserRepository;
import me.wly.movie_reservation.user.model.User;
import me.wly.movie_reservation.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TheaterAdminServiceTest {
    @Mock
    private TheaterRepository theaterRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TheaterAdminRepository theaterAdminRepository;
    @InjectMocks
    private TheaterAdminService theaterAdminService;

    @Test
    void assignAdmin_promotesUserAndCreatesTheaterAssociation() {
        Theater theater = new Theater();
        theater.setId(1);
        User customer = new User();
        customer.setId(10L);
        customer.setUsername("manager");
        customer.setUserRole(UserRole.CUSTOMER);
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater));
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(theaterAdminRepository.existsByUser_IdAndTheater_Id(10L, 1)).thenReturn(false);
        when(theaterAdminRepository.save(any(TheaterAdmin.class))).thenAnswer(invocation -> {
            TheaterAdmin relation = invocation.getArgument(0);
            relation.setId(99L);
            return relation;
        });

        TheaterAdminVO result = theaterAdminService.assignAdmin(1, new TheaterAdminAssignDTO(10L));

        assertEquals(UserRole.THEATER_ADMIN, customer.getUserRole());
        assertEquals(99L, result.id());
        assertEquals(1, result.theaterId());
        assertEquals(10L, result.userId());
        verify(theaterAdminRepository).save(any(TheaterAdmin.class));
    }

    @Test
    void assignAdmin_rejectsDuplicateTheaterAssignment() {
        Theater theater = new Theater();
        theater.setId(1);
        User user = new User();
        user.setId(10L);
        user.setUserRole(UserRole.CUSTOMER);
        when(theaterRepository.findById(1)).thenReturn(Optional.of(theater));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(theaterAdminRepository.existsByUser_IdAndTheater_Id(10L, 1)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> theaterAdminService.assignAdmin(1, new TheaterAdminAssignDTO(10L)));

        verify(theaterAdminRepository, never()).save(any());
    }
}
