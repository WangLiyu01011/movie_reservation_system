package me.wly.movie_reservation.ai;

import me.wly.movie_reservation.ai.model.AiChatSession;
import me.wly.movie_reservation.ai.model.AiChatSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, String> {
    Optional<AiChatSession> findByIdAndUser_Id(String id, Long userId);

    Page<AiChatSession> findByUser_IdAndStatusOrderByUpdatedAtDesc(
            Long userId, AiChatSessionStatus status, Pageable pageable);
}
