package me.wly.movie_reservation.movie;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.movie.dto.MovieSearchDTO;
import me.wly.movie_reservation.movie.vo.MoviePageVO;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieQueryService {
    private final MovieRepository movieRepository;
    private final MovieMapper movieMapper;
    private final Validator validator;
    private final Clock businessClock;

    @Transactional(readOnly = true)
    public MoviePageVO search(MovieSearchDTO request) {
        if (request == null) throw badRequest("Movie search parameters are required");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw badRequest(violations.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted().collect(Collectors.joining("; ")));
        }
        // JPA offsets are int-valued even though Pageable#getOffset returns a long.
        if ((long) request.page() * request.size() > Integer.MAX_VALUE) {
            throw badRequest("Pagination offset is too large");
        }
        String keyword = normalize(request.keyword());
        String titlePattern = keyword == null ? null : "%" + keyword.replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
        String genre = normalize(request.genre());
        var pageable = PageRequest.of(request.page(), request.size());
        var now = LocalDateTime.now(businessClock);
        var result = switch (request.status()) {
            case SHOWING -> movieRepository.searchShowing(now, titlePattern, genre, pageable);
            case UPCOMING -> movieRepository.searchUpcoming(now, titlePattern, genre, pageable);
        };
        return new MoviePageVO(result.getContent().stream().map(movieMapper::entityToListItemVO).toList(),
                request.page(), request.size(), result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private BusinessException badRequest(String message) { return new BusinessException(ResultCode.BAD_REQUEST, message); }
}
