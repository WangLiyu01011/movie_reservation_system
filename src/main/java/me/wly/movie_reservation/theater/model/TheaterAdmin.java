package me.wly.movie_reservation.theater.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.wly.movie_reservation.user.model.User;

@Entity
@Table(
        name = "theater_admin",
        uniqueConstraints = @UniqueConstraint(columnNames = {"theater_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TheaterAdmin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
