package me.wly.movie_reservation.ai.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.wly.movie_reservation.ai.protocol.AiResponseType;
import me.wly.movie_reservation.ai.vo.AiResponseVO;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_chat_turn", uniqueConstraints =
        @UniqueConstraint(name = "uk_ai_chat_turn_session_request", columnNames = {"session_id", "request_id"}),
        indexes = @Index(name = "idx_ai_chat_turn_session_id", columnList = "session_id,id"))
public class AiChatTurn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AiChatSession session;

    @Column(name = "request_id", length = 128, nullable = false, updatable = false)
    private String requestId;

    @Column(name = "user_message", nullable = false, columnDefinition = "text", updatable = false)
    private String userMessage;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private AiChatTurnStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", length = 32)
    private AiResponseType responseType;

    @Column(name = "response_schema_version")
    private Integer responseSchemaVersion;

    /** 返回给客户端的Json原文，包含原序的blocks */
    @Column(name = "response_json", columnDefinition = "longtext")
    private String responseJson;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public AiChatTurn(AiChatSession session, String requestId, String userMessage) {
        this.session = Objects.requireNonNull(session);
        this.requestId = Objects.requireNonNull(requestId);
        this.userMessage = Objects.requireNonNull(userMessage);
        this.status = AiChatTurnStatus.PENDING;
    }

    public void complete(AiResponseVO response, String responseJson, LocalDateTime completedAt) {
        if (status != AiChatTurnStatus.PENDING) {
            throw new IllegalStateException("Only a pending turn can be completed");
        }
        Objects.requireNonNull(response);
        this.responseJson = Objects.requireNonNull(responseJson);
        this.responseType = response.type();
        this.responseSchemaVersion = response.schemaVersion();
        this.completedAt = Objects.requireNonNull(completedAt);
        this.status = AiChatTurnStatus.COMPLETED;
    }

    public void fail(LocalDateTime completedAt) {
        if (status != AiChatTurnStatus.PENDING) {
            throw new IllegalStateException("Only a pending turn can fail");
        }
        this.completedAt = Objects.requireNonNull(completedAt);
        this.status = AiChatTurnStatus.FAILED;
    }
}
