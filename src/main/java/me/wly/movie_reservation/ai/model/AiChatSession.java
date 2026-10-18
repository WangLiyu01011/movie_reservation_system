package me.wly.movie_reservation.ai.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.wly.movie_reservation.user.model.User;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_chat_session", indexes =
        @Index(name = "idx_ai_chat_session_user_updated", columnList = "user_id,updated_at"))
public class AiChatSession {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Setter
    @Column(length = 80)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private AiChatSessionStatus status;

    /** Validated filters and displayed candidate IDs can be stored here in a later service step. */
    @Setter
    @Column(name = "business_state_json", columnDefinition = "longtext")
    private String businessStateJson;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public AiChatSession(User user) {
        this.id = UUID.randomUUID().toString();
        this.user = Objects.requireNonNull(user);
        this.status = AiChatSessionStatus.ACTIVE;
    }

    public void archive() {
        this.status = AiChatSessionStatus.ARCHIVED;
    }
}
