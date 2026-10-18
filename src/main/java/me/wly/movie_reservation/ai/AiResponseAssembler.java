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
import me.wly.movie_reservation.showtime.dto.ShowtimeCandidateDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchResultDTO;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiResponseAssembler {
    private final Validator validator;

    public AiResponseVO assemble(Result<AiAnswerDTO> result) {
        require(result != null && result.content() != null);
        require(result.finishReason() == null || result.finishReason() == FinishReason.STOP);
        AiAnswerDTO answer = result.content();
        require(validator.validate(answer).isEmpty());
        Map<Integer, ShowingMovieVO> movies = new HashMap<>();
        Map<Long, ShowtimeCandidateDTO> showtime = new HashMap<>();
        List<AiResponseVO.ShowtimeQueryPage> pages = new ArrayList<>();
        boolean movieQueryExecuted = false;
        for (var execution : result.toolExecutions()) {
            require(!execution.hasFailed());
            switch (execution.request().name()) {
                case "get_showing_movies" -> {
                    require(execution.resultObject() instanceof List<?>);
                    movieQueryExecuted = true;
                    for (Object item : (List<?>) execution.resultObject()) {
                        require(item instanceof ShowingMovieVO);
                        var movie = (ShowingMovieVO) item;
                        movies.put(movie.movieId(), movie);
                    }
                }
                case "get_showtime_candidates" -> {
                    require(execution.resultObject() instanceof ShowtimeSearchResultDTO);
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
            require(movieIds.add(item.movieId()) && movies.containsKey(item.movieId()));
            return new AiBlock.MovieCardData(movies.get(item.movieId()), item.reason());
        }).toList();
        Set<Long> showtimeIds = new HashSet<>();
        var options = answer.showtimes().stream().map(item -> {
            require(showtimeIds.add(item.showtimeId()) && showtime.containsKey(item.showtimeId()));
            return new AiBlock.ShowtimeCardData(showtime.get(item.showtimeId()), item.reason());
        }).toList();
        require(new HashSet<>(answer.missingFields()).size() == answer.missingFields().size());
        require((answer.type() == AiResponseType.CLARIFICATION) == !answer.missingFields().isEmpty());
        switch (answer.type()) {
            case MOVIE_RECOMMENDATION -> require(!movieCards.isEmpty());
            case SHOWTIME_OPTIONS -> require(!options.isEmpty());
            case NO_RESULTS -> {
                require(movieCards.isEmpty() && options.isEmpty());
                require(pages.isEmpty() ? movieQueryExecuted && movies.isEmpty()
                        : showtime.isEmpty() && pages.stream().noneMatch(AiResponseVO.ShowtimeQueryPage::hasMore));
            }
            case GENERAL -> require(movieCards.isEmpty() && options.isEmpty());
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

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid AI response");
    }
}
