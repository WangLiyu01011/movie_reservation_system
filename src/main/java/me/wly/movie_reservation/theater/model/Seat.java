package me.wly.movie_reservation.theater.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(
        name = "seat",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_seat_hall_position", columnNames = {"hall_id", "x", "y"}),
                @UniqueConstraint(name = "uk_seat_hall_label", columnNames = {"hall_id", "seat_label"})
        }
)
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Integer x;
    @Column(nullable = false)
    private Integer y;

    @ManyToOne
    @JoinColumn(name = "hall_id", nullable = false)
    private Hall hall;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", length = 20, nullable = false)
    private SeatType seatType;
    @Column(name = "seat_label", length = 16, nullable = false)
    private String seatLabel;
}
