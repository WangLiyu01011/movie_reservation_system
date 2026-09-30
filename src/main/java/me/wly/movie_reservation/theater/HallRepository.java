package me.wly.movie_reservation.theater;

import me.wly.movie_reservation.theater.model.Hall;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HallRepository extends JpaRepository<Hall, Integer> {
    /** 通过悲观写锁对排场事务进行序列化处理 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hall from Hall hall where hall.id = :hallId")
    Optional<Hall> findByIdForUpdate(@Param("hallId") Integer hallId);

    boolean existsByTheater_IdAndName(Integer theaterId, String name);
    boolean existsByTheater_IdAndNameAndIdNot(Integer theaterId, String name, Integer id);
}
