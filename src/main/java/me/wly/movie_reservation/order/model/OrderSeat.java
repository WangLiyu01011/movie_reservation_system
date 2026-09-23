package me.wly.movie_reservation.order.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;

import java.math.BigDecimal;

@Entity
@Table(name = "order_seat", indexes = {
        @Index(name = "idx_order_seat_order", columnList = "order_id"),
        @Index(name = "idx_order_seat_showtime_seat", columnList = "showtime_seat_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_seat_id", nullable = false)
    private ShowtimeSeat showtimeSeat;
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal ticketPrice;
}
