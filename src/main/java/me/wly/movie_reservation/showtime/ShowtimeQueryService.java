package me.wly.movie_reservation.showtime;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.movie.MovieRepository;
import me.wly.movie_reservation.showtime.dto.ShowtimeCandidateDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchResultDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSeatCountDTO;
import me.wly.movie_reservation.theater.AreaRepository;
import me.wly.movie_reservation.theater.model.Area;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShowtimeQueryService {
    private static final int MAX_QUERY_DAYS = 31;

    private final ShowtimeCandidateRepository candidateRepository;
    private final ShowtimeSeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final AreaRepository areaRepository;
    private final Validator validator;
    private final Clock businessClock;

    @Transactional(readOnly = true)
    public ShowtimeSearchResultDTO searchCandidates(ShowtimeSearchDTO request) {
        // 先进行参数合法性校验
        validateParameters(request);
        if (!movieRepository.existsById(request.movieId())) {
            throw new BusinessException(ResultCode.MOVIE_NOT_FOUND, "Movie not found: " + request.movieId());
        }
        Area city = areaRepository.findById(request.cityId())
                .orElseThrow(() -> badRequest("City not found: " + request.cityId()));
        if (city.getLevel() != 1) {
            throw badRequest("cityId must refer to a city");
        }
        if (request.districtId() != null) {
            Area district = areaRepository.findById(request.districtId())
                    .orElseThrow(() -> badRequest("District not found: " + request.districtId()));
            if (district.getLevel() != 2 || district.getParentId() != city.getId()) {
                throw badRequest("District must belong to the selected city");
            }
        }

        LocalDateTime now = LocalDateTime.now(businessClock);
        if (!request.startTo().isAfter(now)) {
            return new ShowtimeSearchResultDTO(List.of(), request.page(), request.size(), false);
        }
        List<ShowtimeCandidateDTO> rows = candidateRepository.findCandidates(request, now);
        boolean hasMore = rows.size() > request.size();
        List<ShowtimeCandidateDTO> page = rows.subList(0, Math.min(rows.size(), request.size()));
        if (page.isEmpty()) {
            return new ShowtimeSearchResultDTO(List.of(), request.page(), request.size(), false);
        }

        List<Long> showtimeIds = page.stream().map(ShowtimeCandidateDTO::showtimeId).toList();
        Map<Long, Long> counts = seatRepository.countAvailableByShowtimeIds(showtimeIds).stream()
                .collect(Collectors.toMap(ShowtimeSeatCountDTO::showtimeId,
                        ShowtimeSeatCountDTO::availableSeatCount));
        List<ShowtimeCandidateDTO> items = page.stream()
                .map(candidate -> candidate.withAvailableSeatCount(counts.getOrDefault(candidate.showtimeId(), 0L)))
                .toList();
        return new ShowtimeSearchResultDTO(items, request.page(), request.size(), hasMore);
    }

    private void validateParameters(ShowtimeSearchDTO request) {
        if (request == null) {
            throw badRequest("Search parameters are required");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted().collect(Collectors.joining("; "));
            throw badRequest(message);
        }
        if (!request.startTo().isAfter(request.startFrom())) {
            throw badRequest("startTo must be after startFrom");
        }
        if (Duration.between(request.startFrom(), request.startTo()).compareTo(Duration.ofDays(MAX_QUERY_DAYS)) > 0) {
            throw badRequest("Showtime search range must not exceed " + MAX_QUERY_DAYS + " days");
        }
        if ((long) request.page() * request.size() > Integer.MAX_VALUE) {
            throw badRequest("Pagination offset is too large");
        }
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST, message);
    }
}
