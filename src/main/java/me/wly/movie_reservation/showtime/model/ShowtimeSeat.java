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
@Table(
        name = "showtime_seat",
        uniqueConstraints = @UniqueConstraint(name = "uk_showtime_seat", columnNames = {"showtime_id", "seat_id"}),
        indexes = {
                @Index(name = "idx_showtime_seat_status", columnList = "showtime_id,status"),
                @Index(name = "idx_showtime_seat_order_status", columnList = "order_id,status")
        }
)
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
    @Column(length = 16, nullable = false)
    private SeatStatus status;
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;
    @Column(name = "lock_token", length = 80)
    private String lockToken;
    @Column(name = "lock_until")
    private LocalDateTime lockUntil;
    @Version
    @Column(nullable = false)
    private Long version;
    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type_snapshot", length = 20, nullable = false)
    private SeatType seatTypeSnapshot;
    @Column(name = "seat_label_snapshot", length = 16, nullable = false)
    private String seatLabelSnapshot;
}
