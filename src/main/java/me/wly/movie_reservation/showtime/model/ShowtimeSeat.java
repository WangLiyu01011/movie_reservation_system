package me.wly.movie_reservation.showtime.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.theater.model.Seat;
import me.wly.movie_reservation.theater.model.SeatType;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimeSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;
    @ManyToOne
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;
    private String lockToken;
    private LocalDateTime lockUntil;
    @Version
    private Long version;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatType seatTypeSnapshot;
    @Column(nullable = false)
    private String seatLabelSnapshot;
}
