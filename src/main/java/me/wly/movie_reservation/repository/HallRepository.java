package me.wly.movie_reservation.repository;

import me.wly.movie_reservation.model.entity.Hall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HallRepository extends JpaRepository<Hall, Integer> {
    boolean existsByTheater_IdAndName(Integer theaterId, String name);
    boolean existsByTheater_IdAndNameAndIdNot(Integer theaterId, String name, Integer id);
}
