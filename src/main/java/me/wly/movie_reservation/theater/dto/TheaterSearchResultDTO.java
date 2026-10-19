package me.wly.movie_reservation.theater.dto;

import me.wly.movie_reservation.theater.vo.TheaterVO;
import java.util.List;

public record TheaterSearchResultDTO(Integer cityId, Integer districtId, List<TheaterVO> items, boolean hasMore) {
    public TheaterSearchResultDTO { items = List.copyOf(items); }
}
