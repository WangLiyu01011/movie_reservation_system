package me.wly.movie_reservation.ai;

import dev.langchain4j.model.chat.ChatModel;
import me.wly.movie_reservation.ai.vo.ShowingMovieVO;
import me.wly.movie_reservation.movie.MovieService;
import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.common.time.BusinessTimeConfiguration;
import me.wly.movie_reservation.showtime.ShowtimeQueryService;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = AiWiringTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "langchain4j.open-ai.chat-model.api-key=test-key"
)
class AiWiringTest {

    @MockitoBean
    private MovieService movieService;

    @MockitoBean
    private ShowtimeQueryService showtimeQueryService;

    @MockitoBean
    private me.wly.movie_reservation.theater.AreaSearchService areaSearchService;

    @MockitoBean
    private me.wly.movie_reservation.theater.TheaterService theaterService;

    @Autowired
    private AiAssistantFactory assistantFactory;

    @MockitoBean
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    @Autowired
    private AiTools aiTools;

    @Autowired
    @Qualifier("movieChatModel")
    private ChatModel movieChatModel;

    @Test
    void registersAiServiceModelAndTools() {
        assertThat(assistantFactory.start("[]").assistant()).isNotNull();
        assertThat(movieChatModel).isNotNull();
        assertThat(aiTools).isNotNull();
    }

    @Test
    void mapsShowingMoviesToUsefulToolResults() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 23, 11, 0);
        Movie movie = new Movie(
                1,
                "tt-test",
                now.minusDays(1),
                now.plusDays(1),
                "测试电影",
                "用于验证 AI 工具返回值",
                List.of("剧情"),
                "中文",
                "https://example.com/poster.jpg"
        );
        when(movieService.getShowingMoviesOn(now, null, null))
                .thenReturn(new SliceImpl<>(List.of(movie), PageRequest.of(0, 5), false));

        var result = aiTools.getShowingMovies(now.toString(), null);

        assertThat(result.size()).isEqualTo(5);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextPage()).isNull();
        assertThat(result.items()).containsExactly(new ShowingMovieVO(
                1,
                "tt-test",
                "测试电影",
                "用于验证 AI 工具返回值",
                List.of("剧情"),
                "中文",
                "https://example.com/poster.jpg"
        ));
    }

    @Test
    void showtimeToolUsesTheSameSearchServiceAndDefaultsAsHttpRequests() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 28, 12, 0);
        ShowtimeSearchDTO request = new ShowtimeSearchDTO(1, 100, null,
                start, start.plusHours(6), null, null, null);
        ShowtimeSearchResultDTO expected = new ShowtimeSearchResultDTO(List.of(), 0, 10, false);
        when(showtimeQueryService.searchCandidates(request)).thenReturn(expected);

        assertThat(aiTools.getShowtimeCandidates(1, 100, null, start.toString(), start.plusHours(6).toString(),
                null, null, null)).isSameAs(expected);
        verify(showtimeQueryService).searchCandidates(request);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class,
            DataRedisAutoConfiguration.class,
            DataRedisRepositoriesAutoConfiguration.class
    })
    @ComponentScan(basePackageClasses = AiController.class)
    @Import(BusinessTimeConfiguration.class)
    static class TestApplication {
    }
}
