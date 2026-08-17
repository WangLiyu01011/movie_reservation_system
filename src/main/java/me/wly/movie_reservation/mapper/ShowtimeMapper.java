package me.wly.movie_reservation.mapper;

import me.wly.movie_reservation.model.dto.ShowtimeDTO;
import me.wly.movie_reservation.model.entity.Showtime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ShowtimeMapper {

    @Mapping(target = "startTime")
    @Mapping(target = "endTime")
    @Mapping(target = "hallName")
    @Mapping(target = "price")
    ShowtimeDTO entityToDTO(Showtime showtime);

    List<ShowtimeDTO> toDTOList (List<Showtime> showtimeList);
}
