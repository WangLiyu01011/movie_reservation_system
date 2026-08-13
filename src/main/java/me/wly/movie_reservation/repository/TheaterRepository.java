package me.wly.movie_reservation.repository;

import me.wly.movie_reservation.model.entity.Theater;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TheaterRepository extends JpaRepository<Theater, Integer> {
    List<Theater> findTheaterByDistrict_Id(Integer districtId);
    List<Theater> findTheaterByCity_Id(Integer cityId);
}
