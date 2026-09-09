package me.wly.movie_reservation.repository;

import me.wly.movie_reservation.model.entity.TheaterAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TheaterAdminRepository extends JpaRepository<TheaterAdmin, Long> {
    boolean existsByUser_IdAndTheater_Id(Long userId, Integer theaterId);
}
