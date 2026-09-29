package me.wly.movie_reservation.showtime;

import me.wly.movie_reservation.common.exception.GlobalExceptionHandler;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShowtimeSearchHttpTest {
    private final ShowtimeService showtimeService = mock(ShowtimeService.class);
    private final ShowtimeQueryService queryService = mock(ShowtimeQueryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ShowtimeController(showtimeService, queryService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void bindsSearchParametersAndDefaultsWithoutUsingTheLegacyEndpoint() throws Exception {
        when(queryService.searchCandidates(any())).thenReturn(new ShowtimeSearchResultDTO(List.of(), 0, 10, false));
        mvc.perform(get("/api/v1/showtimes/search")
                        .param("movieId", "12").param("cityId", "100")
                        .param("startFrom", "2026-09-28T12:00:00").param("startTo", "2026-09-28T18:00:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.hasMore").value(false));
        verify(queryService).searchCandidates(new ShowtimeSearchDTO(12, 100, null,
                LocalDateTime.of(2026, 9, 28, 12, 0), LocalDateTime.of(2026, 9, 28, 18, 0), 1, 0, 10));
        verifyNoInteractions(showtimeService);
    }

    @Test
    void missingRequiredParametersReturns400() throws Exception {
        mvc.perform(get("/api/v1/showtimes/search")).andExpect(status().isBadRequest());
        verifyNoInteractions(queryService);
    }

    @Test
    void invalidDateReturns400() throws Exception {
        mvc.perform(get("/api/v1/showtimes/search").param("movieId", "12").param("cityId", "100")
                        .param("startFrom", "tomorrow").param("startTo", "2026-09-28T18:00:00"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(queryService);
    }

    @Test
    void excessivePageSizeReturns400() throws Exception {
        mvc.perform(get("/api/v1/showtimes/search").param("movieId", "12").param("cityId", "100")
                        .param("startFrom", "2026-09-28T12:00:00").param("startTo", "2026-09-28T18:00:00")
                        .param("size", "21"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(queryService);
    }

    @Test
    void legacyTheaterQueryStillAcceptsImdbIdUnderItsOriginalParameterName() throws Exception {
        when(showtimeService.getShowtimeList(1, "tt-test")).thenReturn(List.of());
        mvc.perform(get("/api/v1/showtimes").param("theaterId", "1").param("movieId", "tt-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verify(showtimeService).getShowtimeList(1, "tt-test");
        verifyNoInteractions(queryService);
    }
}
