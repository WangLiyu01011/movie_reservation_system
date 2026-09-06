package me.wly.movie_reservation.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import me.wly.movie_reservation.model.enum_class.SeatStatus;
import me.wly.movie_reservation.model.enum_class.SeatType;

@Entity
@AllArgsConstructor
@NoArgsConstructor
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
