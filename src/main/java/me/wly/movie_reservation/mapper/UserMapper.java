package me.wly.movie_reservation.mapper;

import me.wly.movie_reservation.model.entity.User;
import me.wly.movie_reservation.model.vo.UserVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserVO entityToVO(User user);
}
