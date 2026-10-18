package me.wly.movie_reservation.ai;

import me.wly.movie_reservation.ai.model.AiChatTurn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiChatTurnRepository extends JpaRepository<AiChatTurn, Long> {
    Optional<AiChatTurn> findBySession_IdAndSession_User_IdAndRequestId(
            String sessionId, Long userId, String requestId);

    Page<AiChatTurn> findBySession_IdAndSession_User_IdOrderByIdDesc(
            String sessionId, Long userId, Pageable pageable);
}
