package me.wly.movie_reservation.service;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.mapper.ShowtimeMapper;
import me.wly.movie_reservation.model.dto.ShowtimeCreateDTO;
import me.wly.movie_reservation.model.dto.ShowtimeDTO;
import me.wly.movie_reservation.model.entity.Hall;
import me.wly.movie_reservation.model.entity.Movie;
import me.wly.movie_reservation.model.entity.Showtime;
import me.wly.movie_reservation.model.entity.Theater;
import me.wly.movie_reservation.repository.HallRepository;
import me.wly.movie_reservation.repository.MovieRepository;
import me.wly.movie_reservation.repository.ShowtimeRepository;
import me.wly.movie_reservation.repository.TheaterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowtimeService {
    private final ShowtimeRepository showtimeRepository;
    private final TheaterRepository theaterRepository;
    private final HallRepository hallRepository;
    private final MovieRepository movieRepository;
    private final ShowtimeMapper showtimeMapper;

    public List<ShowtimeDTO> getShowtimeList(Integer theaterId, String movieImdbId){
        if(movieImdbId == null) {
            List<Showtime> showtimeList = showtimeRepository.findShowtimeByTheater_Id(theaterId);
            return showtimeMapper.toDTOList(showtimeList);
        }
        List<Showtime> showtimeList = showtimeRepository.findShowtimeByTheater_IdAndMovie_ImdbId(theaterId, movieImdbId);
        return showtimeMapper.toDTOList(showtimeList);
    }

    @Transactional
    public ShowtimeDTO createShowtime(ShowtimeCreateDTO newShowtime) {
        Theater theater = theaterRepository.findById(newShowtime.theaterId()).orElseThrow(()->new BusinessException(ResultCode.THEATER_NOT_FOUND, "Target theater not found: " + newShowtime.theaterId()));
        Hall hall = hallRepository.findById(newShowtime.hallId()).orElseThrow(()->new BusinessException(ResultCode.HALL_NOT_FOUND, "Target hall not found: " + newShowtime.hallId()));
        Movie movie = movieRepository.findMovieByImdbId(newShowtime.imdbId()).orElseThrow(()->new BusinessException(ResultCode.MOVIE_NOT_FOUND, "Target hall not found: " + newShowtime.imdbId()));
        Showtime showtime = new Showtime();
        showtime.setStartTime(newShowtime.startTime());
        showtime.setEndTime(newShowtime.endTime());
        showtime.setPrice(newShowtime.price());
        showtime.setTheater(theater);
        showtime.setHall(hall);
        showtime.setHallName(hall.getName());
        showtime.setMovie(movie);
        showtime.setMovieTitle(movie.getTitle());

        Showtime saved = showtimeRepository.save(showtime);
        return showtimeMapper.entityToDTO(saved);
    }
}
