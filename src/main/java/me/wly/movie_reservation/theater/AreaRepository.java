package me.wly.movie_reservation.theater;

import me.wly.movie_reservation.theater.model.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AreaRepository extends JpaRepository<Area, Integer> {
    @Query("""
            select a from Area a
            where a.level = :level and a.name in :names
              and (:parentId is null or a.parentId = :parentId)
            order by a.id
            """)
    List<Area> findExactCandidates(@Param("level") short level, @Param("names") Collection<String> names,
                                   @Param("parentId") Integer parentId, Pageable limit);

    @Query("""
            select a from Area a
            where a.level = :level and a.name like :pattern escape '!'
              and (:parentId is null or a.parentId = :parentId)
            order by a.id
            """)
    List<Area> findPrefixCandidates(@Param("level") short level, @Param("pattern") String pattern,
                                    @Param("parentId") Integer parentId, Pageable limit);
}
