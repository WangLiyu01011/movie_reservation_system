package me.wly.movie_reservation.showtime;

import me.wly.movie_reservation.showtime.dto.ShowtimeDTO;
import me.wly.movie_reservation.showtime.model.Showtime;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ShowtimeMapper {
    ShowtimeDTO entityToDTO(Showtime showtime);

    List<ShowtimeDTO> toDTOList (List<Showtime> showtimeList);
}
