package me.wly.movie_reservation.theater.dto;

import java.util.List;

public record AreaSearchResultDTO(String keyword, Status status, List<AreaCandidateDTO> candidates, boolean hasMore) {
    public enum Status { MATCHED, AMBIGUOUS, NOT_FOUND }

    public AreaSearchResultDTO {
        candidates = List.copyOf(candidates);
    }
}
