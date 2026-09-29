package me.wly.movie_reservation.showtime;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.showtime.dto.ShowtimeCandidateDTO;
import me.wly.movie_reservation.showtime.dto.ShowtimeSearchDTO;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.Showtime;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import me.wly.movie_reservation.movie.model.Movie;
import me.wly.movie_reservation.theater.model.Area;
import me.wly.movie_reservation.theater.model.Hall;
import me.wly.movie_reservation.theater.model.Theater;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ShowtimeCandidateRepository {
    private final EntityManager entityManager;

    public List<ShowtimeCandidateDTO> findCandidates(ShowtimeSearchDTO request, LocalDateTime now) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ShowtimeCandidateDTO> query = cb.createQuery(ShowtimeCandidateDTO.class);
        Root<Showtime> showtime = query.from(Showtime.class);
        Join<Showtime, Movie> movie = showtime.join("movie");
        Join<Showtime, Theater> theater = showtime.join("theater");
        Join<Showtime, Hall> hall = showtime.join("hall");
        Join<Theater, Area> city = theater.join("city");
        Join<Theater, Area> district = theater.join("district");

        // 分页之前筛选可用座位数量，
        Subquery<Long> availableSeats = query.subquery(Long.class);
        Root<ShowtimeSeat> seat = availableSeats.from(ShowtimeSeat.class);
        availableSeats.select(cb.count(seat)).where(
                cb.equal(seat.get("showtime").get("id"), showtime.get("id")),
                cb.equal(seat.get("status"), SeatStatus.AVAILABLE));

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(movie.get("id"), request.movieId()));
        predicates.add(cb.equal(city.get("id"), request.cityId()));
        if (request.districtId() != null) {
            predicates.add(cb.equal(district.get("id"), request.districtId()));
        }
        predicates.add(cb.greaterThanOrEqualTo(showtime.get("startTime"), request.startFrom()));
        predicates.add(cb.lessThan(showtime.get("startTime"), request.startTo()));
        predicates.add(cb.greaterThan(showtime.get("startTime"), now));
        predicates.add(cb.equal(hall.get("status"), "ACTIVE"));
        predicates.add(cb.ge(availableSeats, request.minAvailableSeats()));

        // Select scalar fields instead of entities: eager associations cannot cause N+1 queries.
        query.select(cb.construct(ShowtimeCandidateDTO.class,
                        showtime.get("id"), movie.get("id"), movie.get("imdbId"),
                        showtime.get("movieTitle"), theater.get("id"), showtime.get("theaterName"),
                        theater.get("location"), city.get("id"), city.get("name"),
                        district.get("id"), district.get("name"), hall.get("id"),
                        showtime.get("hallName"), hall.get("type"), showtime.get("startTime"),
                        showtime.get("endTime"), showtime.get("price"), cb.literal(0L)))
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(cb.asc(showtime.get("startTime")), cb.asc(showtime.get("id")));

        return entityManager.createQuery(query)
                .setFirstResult(Math.toIntExact((long) request.page() * request.size()))
                .setMaxResults(request.size() + 1)
                .getResultList();
    }
}
