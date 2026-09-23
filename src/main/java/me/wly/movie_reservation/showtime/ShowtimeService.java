package me.wly.movie_reservation.showtime;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.movie.MovieRepository;
import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.showtime.dto.ShowtimeCreateDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeDTO;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.Showtime;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import me.wly.movie_reservation.theater.HallRepository;
import me.wly.movie_reservation.theater.SeatRepository;
import me.wly.movie_reservation.theater.TheaterAdminRepository;
import me.wly.movie_reservation.theater.TheaterRepository;
import me.wly.movie_reservation.theater.model.*;
import me.wly.movie_reservation.user.UserRepository;
import me.wly.movie_reservation.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShowtimeService {
    private final ShowtimeRepository showtimeRepository;
    private final TheaterRepository theaterRepository;
    private final TheaterAdminRepository theaterAdminRepository;
    private final HallRepository hallRepository;
    private final MovieRepository movieRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final ShowtimeMapper showtimeMapper;
    private final UserRepository userRepository;

    public List<ShowtimeDTO> getShowtimeList(Integer theaterId, String movieImdbId){
        if(movieImdbId == null) {
            List<Showtime> showtimeList = showtimeRepository.findShowtimeByTheater_Id(theaterId);
            return showtimeMapper.toDTOList(showtimeList);
        }
        List<Showtime> showtimeList = showtimeRepository.findShowtimeByTheater_IdAndMovie_ImdbId(theaterId, movieImdbId);
        return showtimeMapper.toDTOList(showtimeList);
    }

    @Transactional
    public ShowtimeDTO createShowtime(ShowtimeCreateDTO newShowtime, String username) {
        User creator = userRepository.findByUsername(username)
                .orElseThrow(()->new BusinessException(ResultCode.USER_NOT_FOUND, "Current user not found"));
        if(!theaterAdminRepository.existsByUser_IdAndTheater_Id(creator.getId(), newShowtime.theaterId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "No permission to manage target theater");
        };


        if (!newShowtime.endTime().isAfter(newShowtime.startTime())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Showtime end time must be after start time");
        }

        Theater theater = theaterRepository.findById(newShowtime.theaterId())
                .orElseThrow(()->new BusinessException(ResultCode.THEATER_NOT_FOUND, "Target theater not found: " + newShowtime.theaterId()));

        Hall hall = hallRepository.findById(newShowtime.hallId())
                .orElseThrow(()->new BusinessException(ResultCode.HALL_NOT_FOUND, "Target hall not found: " + newShowtime.hallId()));

        if (!hall.getTheater().getId().equals(theater.getId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Hall does not belong to the target theater");
        }

        if (showtimeRepository.existsByHall_IdAndStartTimeLessThanAndEndTimeGreaterThan(
                hall.getId(), newShowtime.endTime(), newShowtime.startTime())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Showtime overlaps an existing showtime in this hall");
        }

        Movie movie = movieRepository.findMovieByImdbId(newShowtime.imdbId())
                .orElseThrow(
                        ()->new BusinessException(ResultCode.MOVIE_NOT_FOUND, "Target movie not found: " + newShowtime.imdbId())
                );

        List<Seat> seats = seatRepository.findAllByHall_IdOrderByXAscYAsc(hall.getId()).stream()
                .filter(seat -> seat.getSeatType() != SeatType.EMPTY)
                .toList();

        if (seats.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Cannot create showtime for a hall without sellable seats");
        }

        Showtime showtime = new Showtime();
        showtime.setStartTime(newShowtime.startTime());
        showtime.setEndTime(newShowtime.endTime());
        showtime.setPrice(newShowtime.price());
        showtime.setTheater(theater);
        showtime.setTheaterName(theater.getTheaterName());
        showtime.setHall(hall);
        showtime.setHallName(hall.getName());
        showtime.setMovie(movie);
        showtime.setMovieTitle(movie.getTitle());

        Showtime saved = showtimeRepository.save(showtime);
        List<ShowtimeSeat> showtimeSeats = seats.stream()
                .map(seat -> createAvailableShowtimeSeat(saved, seat))
                .toList();
        showtimeSeatRepository.saveAll(showtimeSeats);

        return showtimeMapper.entityToDTO(saved);
    }

    private ShowtimeSeat createAvailableShowtimeSeat(Showtime showtime, Seat seat) {
        ShowtimeSeat showtimeSeat = new ShowtimeSeat();
        showtimeSeat.setShowtime(showtime);
        showtimeSeat.setSeat(seat);
        showtimeSeat.setStatus(SeatStatus.AVAILABLE);
        showtimeSeat.setSeatTypeSnapshot(seat.getSeatType());
        showtimeSeat.setSeatLabelSnapshot(seat.getSeatLabel());
        return showtimeSeat;
    }
}
