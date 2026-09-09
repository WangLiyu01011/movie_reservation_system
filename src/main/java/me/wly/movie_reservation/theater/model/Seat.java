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
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer x;
    private Integer y;

    @ManyToOne
    @JoinColumn(name = "hall_id")
    private Hall hall;

    @Enumerated(EnumType.STRING)
    private SeatType seatType;
    private String seatLabel;
}
