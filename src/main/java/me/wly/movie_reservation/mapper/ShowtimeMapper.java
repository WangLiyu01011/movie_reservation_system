package me.wly.movie_reservation.mapper;

import me.wly.movie_reservation.model.dto.ShowtimeDTO;
import me.wly.movie_reservation.model.entity.Showtime;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ShowtimeMapper {
    ShowtimeDTO entityToDTO(Showtime showtime);

    List<ShowtimeDTO> toDTOList (List<Showtime> showtimeList);
}
