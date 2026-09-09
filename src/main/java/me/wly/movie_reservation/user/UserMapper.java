package me.wly.movie_reservation.user;

import me.wly.movie_reservation.user.model.User;
import me.wly.movie_reservation.user.vo.UserLoginVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserLoginVO entityToVO(User user);
}
