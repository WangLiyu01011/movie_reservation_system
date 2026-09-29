package me.wly.movie_reservation.showtime.dto;

import java.util.List;

public record ShowtimeSearchResultDTO(
        List<ShowtimeCandidateDTO> items,
        int page,
        int size,
        boolean hasMore
) {
    public ShowtimeSearchResultDTO {
        items = List.copyOf(items);
    }
}
