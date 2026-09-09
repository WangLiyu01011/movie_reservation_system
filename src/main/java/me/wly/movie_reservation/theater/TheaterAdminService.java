package me.wly.movie_reservation.theater;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.theater.dto.TheaterAdminAssignDTO;
import me.wly.movie_reservation.theater.model.Theater;
import me.wly.movie_reservation.theater.model.TheaterAdmin;
import me.wly.movie_reservation.theater.vo.TheaterAdminVO;
import me.wly.movie_reservation.user.UserRepository;
import me.wly.movie_reservation.user.model.User;
import me.wly.movie_reservation.user.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TheaterAdminService {
    private final TheaterRepository theaterRepository;
    private final UserRepository userRepository;
    private final TheaterAdminRepository theaterAdminRepository;

    @Transactional
    public TheaterAdminVO assignAdmin(Integer theaterId, TheaterAdminAssignDTO dto) {
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new BusinessException(ResultCode.THEATER_NOT_FOUND, "Target theater not found: " + theaterId));
        User user = userRepository.findById(dto.userId())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "Target user not found: " + dto.userId()));

        if (user.getUserRole() == UserRole.SYSTEM_ADMIN) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "System administrator already has access to every theater");
        }
        if (theaterAdminRepository.existsByUser_IdAndTheater_Id(user.getId(), theater.getId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "User is already an administrator of this theater");
        }

        user.setUserRole(UserRole.THEATER_ADMIN);
        TheaterAdmin theaterAdmin = new TheaterAdmin();
        theaterAdmin.setTheater(theater);
        theaterAdmin.setUser(user);
        TheaterAdmin saved = theaterAdminRepository.save(theaterAdmin);

        return new TheaterAdminVO(saved.getId(), theater.getId(), user.getId(), user.getUsername());
    }
}
