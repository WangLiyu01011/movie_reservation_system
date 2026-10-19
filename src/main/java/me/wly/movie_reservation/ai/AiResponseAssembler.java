package me.wly.movie_reservation.ai;

import dev.langchain4j.service.Result;
import dev.langchain4j.model.output.FinishReason;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.ai.dto.AiAnswerDTO;
import me.wly.movie_reservation.ai.protocol.AiResponseType;
import me.wly.movie_reservation.ai.vo.AiBlock;
import me.wly.movie_reservation.ai.vo.AiResponseVO;
import me.wly.movie_reservation.ai.vo.ShowingMovieVO;
import me.wly.movie_reservation.ai.vo.ShowingMoviePageVO;
import me.wly.movie_reservation.showtime.dto.ShowtimeCandidateDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchResultDTO;
import org.springframework.stereotype.Service;
import java.util.*;
import static me.wly.movie_reservation.ai.AiFailureCode.*;

@Service
@RequiredArgsConstructor
public class AiResponseAssembler {
    private final Validator validator;

    public AiResponseVO assemble(Result<AiAnswerDTO> result) {
        require(result != null, RESPONSE_MISSING);
        require(result.content() != null, RESPONSE_CONTENT_MISSING);
        require(result.finishReason() != FinishReason.LENGTH, MODEL_OUTPUT_TRUNCATED);
        require(result.finishReason() == null || result.finishReason() == FinishReason.STOP, FINISH_REASON_INVALID);
        AiAnswerDTO answer = result.content();
        var violations = validator.validate(answer);
        if (!violations.isEmpty()) {
            // Only schema-owned property paths and annotation names, never invalid values or messages.
            var rules = violations.stream().map(violation -> violation.getPropertyPath() + "."
                    + violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
                    .sorted().limit(12).toList();
            throw new AiDiagnosticException(ANSWER_FIELD_INVALID, null, rules);
        }
        Map<Integer, ShowingMovieVO> movies = new HashMap<>();
        Map<Long, ShowtimeCandidateDTO> showtime = new HashMap<>();
        List<AiResponseVO.ShowtimeQueryPage> pages = new ArrayList<>();
        boolean emptyMovieFirstPageExecuted = false;
        for (var execution : result.toolExecutions()) {
            require(!execution.hasFailed(), TOOL_RESULT_FAILED);
            switch (execution.request().name()) {
                case "get_showing_movies", "get_showing_movies_by_genre" -> {
                    require(execution.resultObject() instanceof ShowingMoviePageVO, MOVIE_RESULT_TYPE_INVALID);
                    var query = (ShowingMoviePageVO) execution.resultObject();
                    // Invalid genre is recoverable tool feedback, not evidence of an empty catalogue.
                    if (query.error() != null) break;
                    emptyMovieFirstPageExecuted |= query.page() == 0 && query.items().isEmpty() && !query.hasMore();
                    for (var movie : query.items()) {
                        movies.put(movie.movieId(), movie);
                    }
                }
                case "get_showtime_candidates" -> {
                    require(execution.resultObject() instanceof ShowtimeSearchResultDTO, SHOWTIME_RESULT_TYPE_INVALID);
                    var query = (ShowtimeSearchResultDTO) execution.resultObject();
                    query.items().forEach(item -> showtime.put(item.showtimeId(), item));
                    pages.add(new AiResponseVO.ShowtimeQueryPage(execution.request().id(), query.page(),
                            query.size(), query.hasMore(), query.items().stream()
                            .map(ShowtimeCandidateDTO::showtimeId).toList()));
                }
                default -> { /* 其他工具不返回推荐值 */ }
            }
        }

        Set<Integer> movieIds = new HashSet<>();
        var movieCards = answer.movies().stream().map(item -> {
            require(movieIds.add(item.movieId()), MOVIE_ID_DUPLICATE);
            require(movies.containsKey(item.movieId()), MOVIE_ID_NOT_IN_TOOL_RESULTS);
            return new AiBlock.MovieCardData(movies.get(item.movieId()), item.reason());
        }).toList();
        Set<Long> showtimeIds = new HashSet<>();
        var options = answer.showtimes().stream().map(item -> {
            require(showtimeIds.add(item.showtimeId()), SHOWTIME_ID_DUPLICATE);
            require(showtime.containsKey(item.showtimeId()), SHOWTIME_ID_NOT_IN_TOOL_RESULTS);
            return new AiBlock.ShowtimeCardData(showtime.get(item.showtimeId()), item.reason());
        }).toList();
        require(new HashSet<>(answer.missingFields()).size() == answer.missingFields().size(), MISSING_FIELDS_DUPLICATE);
        if (answer.type() == AiResponseType.CLARIFICATION) {
            require(!answer.missingFields().isEmpty(), CLARIFICATION_FIELDS_REQUIRED);
        } else require(answer.missingFields().isEmpty(), UNEXPECTED_MISSING_FIELDS);
        switch (answer.type()) {
            case MOVIE_RECOMMENDATION -> require(!movieCards.isEmpty(), MOVIE_RECOMMENDATIONS_REQUIRED);
            case SHOWTIME_OPTIONS -> require(!options.isEmpty(), SHOWTIME_OPTIONS_REQUIRED);
            case NO_RESULTS -> {
                require(movieCards.isEmpty() && options.isEmpty(), NO_RESULTS_CONTAINS_CARDS);
                require(pages.isEmpty() ? emptyMovieFirstPageExecuted && movies.isEmpty()
                        : showtime.isEmpty() && pages.stream().noneMatch(AiResponseVO.ShowtimeQueryPage::hasMore),
                        NO_RESULTS_WITHOUT_EMPTY_QUERY);
            }
            case GENERAL -> require(movieCards.isEmpty() && options.isEmpty(), GENERAL_CONTAINS_CARDS);
            case CLARIFICATION -> { /* Cards may accompany the question. */ }
        }
        List<AiBlock> blocks = new ArrayList<>();
        blocks.add(new AiBlock.Text("text-0", new AiBlock.TextData(answer.message())));
        for (int i = 0; i < movieCards.size(); i++) {
            blocks.add(new AiBlock.MovieCard("movie-" + i, movieCards.get(i)));
        }
        for (int i = 0; i < options.size(); i++) {
            blocks.add(new AiBlock.ShowtimeCard("showtime-" + i, options.get(i)));
        }
        if (!answer.missingFields().isEmpty()) {
            blocks.add(new AiBlock.InputRequest("input-0",
                    new AiBlock.InputRequestData(answer.missingFields())));
        }
        if (!pages.isEmpty()) {
            blocks.add(new AiBlock.Notice("notice-0", new AiBlock.NoticeData(
                    "场次查询不会锁座或创建订单；余座以实际下单时为准，数量充足不保证连座。")));
        }
        return new AiResponseVO(AiResponseVO.CURRENT_SCHEMA_VERSION, answer.type(), blocks, pages);
    }

    private static void require(boolean condition, AiFailureCode code) {
        if (!condition) throw new AiDiagnosticException(code);
    }
}
