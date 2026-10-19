package me.wly.movie_reservation.theater;

import me.wly.movie_reservation.theater.model.Theater;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TheaterRepository extends JpaRepository<Theater, Integer> {
    List<Theater> findTheaterByDistrict_Id(Integer districtId);
    List<Theater> findTheaterByCity_Id(Integer cityId);
    boolean existsByDistrict_IdAndTheaterName(Integer districtId, String theaterName);

    @Query("""
            select t from Theater t where t.city.id = :cityId
              and (:districtId is null or t.district.id = :districtId)
            order by t.id
            """)
    List<Theater> findCandidates(@Param("cityId") Integer cityId, @Param("districtId") Integer districtId, Pageable limit);
}
