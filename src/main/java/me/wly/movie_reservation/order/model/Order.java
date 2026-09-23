package me.wly.movie_reservation.order.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.wly.movie_reservation.common.util.UuidCodeGenerator;
import me.wly.movie_reservation.showtime.model.Showtime;
import me.wly.movie_reservation.user.model.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orders_user_request",
                columnNames = {"user_id", "request_id"}
        ),
        indexes = @Index(name = "idx_orders_status_expires_at", columnList = "status,expires_at")
)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 80, nullable = false, unique = true)
    private String code;
    @Column(name = "request_id", nullable = false, length = 128)
    private String requestId;
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "movie_title", length = 80)
    private String movieTitle;
    @Column(name = "theater_name", length = 80)
    private String theaterName;
    @Column(name = "hall_name", length = 40)
    private String hallName;
    @ManyToOne
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;
    @Column(nullable = false)
    private LocalDateTime startTime;
    @Column(nullable = false)
    private LocalDateTime endTime;
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderSeat> orderSeats = new ArrayList<>();
    public void addOrderSeat(OrderSeat orderSeat) {
        orderSeats.add(orderSeat);
        orderSeat.setOrder(this);
    }
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal totalPrice;
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OrderStatus status;
    @Column(name = "expires_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
    @PrePersist
    public void prePersist(){
        this.code = UuidCodeGenerator.generateCode("odr_");
        this.status = OrderStatus.PENDING_PAYMENT;
    }

}
