package me.wly.movie_reservation.ai;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.ai.vo.ShowingMovieVO;
import me.wly.movie_reservation.ai.vo.ShowingMoviePageVO;
import me.wly.movie_reservation.movie.MovieService;
import me.wly.movie_reservation.movie.model.MovieGenre;
import me.wly.movie_reservation.showtime.ShowtimeQueryService;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchResultDTO;
import me.wly.movie_reservation.theater.AreaSearchService;
import me.wly.movie_reservation.theater.TheaterService;
import me.wly.movie_reservation.theater.dto.AreaSearchResultDTO;
import me.wly.movie_reservation.theater.dto.TheaterSearchResultDTO;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AiTools {
    private final MovieService movieService;
    private final ShowtimeQueryService showtimeQueryService;
    private final Clock businessClock;
    private final AreaSearchService areaSearchService;
    private final TheaterService theaterService;

    @Tool(name = "search_areas", value = """
            将用户给出的城市/区县名称解析为数据库中的真实地区ID。keyword只传提取出的地名，不传整句。
            level=1查询城市（默认），level=2查询区县；parentId可限定父地区，查区县时优先传已确认的城市ID。
            支持汕头/汕头市、龙湖/龙湖区等简称。最多返回10项，hasMore表示还有其他候选。
            MATCHED才可直接使用候选ID；AMBIGUOUS必须让用户根据候选及父地区确认，不得擅选第一项；
            NOT_FOUND表示系统未收录或名称有误，不代表该地区没有影院/场次，不得编造ID。
            """)
    public AreaSearchResultDTO searchAreas(
            @P("城市或区县名称，如汕头、龙湖区") String keyword,
            @P(value = "1城市，2区县；默认1", required = false) Integer level,
            @P(value = "父地区ID；城市的父级为省，区县的父级为城市", required = false) Integer parentId) {
        return areaSearchService.search(keyword, level, parentId);
    }

    @Tool(name = "get_theaters", value = """
            根据已解析的城市ID和可选区县ID查询本系统收录的影院名称与地址，最多返回20家。
            地区ID必须来自search_areas工具已确认的候选，不得自行猜测；区县必须属于指定城市。
            hasMore=true时不能宣称结果是全部影院。items为空仅表示系统未收录，不表示现实中没有影院。
            影院查询最终使用GENERAL，回答仅依据工具结果，不创建订单、不查询或承诺余座。
            """)
    public TheaterSearchResultDTO getTheaters(
            @P("已确认的城市ID") Integer cityId,
            @P(value = "可选的区县ID，必须属于该城市", required = false) Integer districtId) {
        return theaterService.searchTheaters(cityId, districtId);
    }

    @Tool(name = "get_current_time", value = "获取服务器当前日期和时间")
    public LocalDateTime getCurrentTime() {
        return LocalDateTime.now(businessClock);
    }

    @Tool(name = "get_day_of_week", value = "获得当日是星期几, 以处理“帮我订购下周一的电影票”这类请求")
    public DayOfWeek getDayOfWeek() {return getCurrentTime().getDayOfWeek();}

    @Tool(
            name = "get_showing_movies",
            value = """
                    分页获取指定时间正在上映的电影，每次固定最多5项，按上映时间及ID降序排列。
                    先获取服务器时间，第一次page=0；本页没有符合用户偏好的电影且hasMore=true时，
                    使用相同time和nextPage按顺序继续查询，禁止跳页或重复翻页；有合适结果即停止。
                    hasMore=true不能声称已查完全部电影。用户明确指定合法类型时优先使用get_showing_movies_by_genre。
                    """
    )
    public ShowingMoviePageVO getShowingMovies(
            @P("ISO-8601日期时间；翻页时沿用首次查询的time") String time,
            @P(value = "页码从0开始，默认0；后续使用上一页nextPage", required = false) Integer page) {
        return moviePage(time, page, null);
    }

    @Tool(name = "get_showing_movies_by_genre", value = """
            从用户自然语言中提取单个明确、肯定的电影类型关键词，再查询该类型正在上映的电影，每页固定5项。
            合法类型：动作、冒险、动画、喜剧、犯罪、纪录、剧情、家庭、奇幻、历史、恐怖、音乐、悬疑、爱情、
            科幻、电视电影、惊悚、战争、西部。支持对应英文名称和“科幻片”等后缀；搞笑映射喜剧、浪漫映射爱情。
            例如“想看一部搞笑的电影”提取喜剧；“想看科幻片”提取科幻。只传关键词，不传原句。
            不得把“不想看恐怖片”提取为想看恐怖；治愈、烧脑、适合约会不是合法类型，不得擅自等同某类型，
            应询问具体类型或使用普通电影工具并依据简介筛选。多个类型不要拼接成一个关键词。
            首次page=0；无合适候选且hasMore=true时，沿用相同time和genre、使用nextPage顺序翻页。
            error不为空表示参数不合法，不是没有电影，应修正关键词或向用户确认。
            """)
    public ShowingMoviePageVO getShowingMoviesByGenre(
            @P("ISO-8601日期时间；翻页时保持相同") String time,
            @P("从用户需求提取的单个合法类型，如喜剧、科幻") String genre,
            @P(value = "页码从0开始，默认0；后续使用上一页nextPage", required = false) Integer page) {
        var resolved = MovieGenre.fromKeyword(genre);
        return resolved.isEmpty() ? ShowingMoviePageVO.invalidGenre() : moviePage(time, page, resolved.get());
    }

    private ShowingMoviePageVO moviePage(String time, Integer page, MovieGenre genre) {
        var result = movieService.getShowingMoviesOn(LocalDateTime.parse(time), page, genre);
        var items = result.getContent().stream()
                .map(movie -> new ShowingMovieVO(
                        movie.getId(),
                        movie.getImdbId(),
                        movie.getTitle(),
                        movie.getDescription(),
                        movie.getGenres(),
                        movie.getLanguage(),
                        movie.getPosterImageURL()
                ))
                .toList();
        return new ShowingMoviePageVO(items, result.getNumber(), 5, result.hasNext(),
                result.hasNext() ? result.getNumber() + 1 : null, genre == null ? null : genre.label(), null,
                java.util.List.of());
    }

    @Tool(name = "get_showtime_candidates", value = """
            按电影内部ID、城市、可选行政区及开始放映时间区间查询未来可购场次。
            时间使用 Asia/Shanghai，区间为 startFrom（含）到 startTo（不含），最多31天。
            quantity只检查可用座位数量，不保证连座；结果不会创建订单或锁座。
            默认 quantity=1、page=0、size=10，size最多20；hasMore=true时可查询下一页。
            movieId必须来自电影查询工具；地区ID优先通过search_areas获取并确认，不得编造。
            """)
    public ShowtimeSearchResultDTO getShowtimeCandidates(
            @P("电影数据库内部ID，不是IMDb ID") Integer movieId,
            @P("城市ID") Integer cityId,
            @P(value = "可选行政区ID，必须属于指定城市", required = false) Integer districtId,
            @P("开始时间下界，ISO-8601格式，例如2026-09-28T12:00:00") String startFrom,
            @P("开始时间上界，不含该时刻，例如2026-09-28T18:00:00") String startTo,
            @P(value = "购票人数，默认1", required = false) Integer quantity,
            @P(value = "页码，从0开始，默认0", required = false) Integer page,
            @P(value = "每页数量，默认10，最大20", required = false) Integer size) {
        return showtimeQueryService.searchCandidates(new ShowtimeSearchDTO(
                movieId, cityId, districtId, LocalDateTime.parse(startFrom),
                LocalDateTime.parse(startTo), quantity, page, size));
    }

}
