package me.wly.movie_reservation.ai;

import me.wly.movie_reservation.ai.vo.AiTurnVO;
import java.util.List;

/** Internal Redis document. Model/tool messages are never returned through history APIs. */
public record AiConversationState(String messagesJson, List<AiTurnVO> turns) {
    public AiConversationState { turns = List.copyOf(turns); }
    public static AiConversationState empty() { return new AiConversationState("[]", List.of()); }
}
