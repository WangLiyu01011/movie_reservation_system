package me.wly.movie_reservation.model.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.wly.movie_reservation.common.utils.UuidCodeGenerator;
import me.wly.movie_reservation.model.enum_class.OrderStatus;

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
        )
)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    @Column(name = "request_id", nullable = false)
    private String requestId;
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    private String movieTitle;
    private String theaterName;
    private String hallName;
    @ManyToOne
    @JoinColumn(name = "showtime_id")
    private Showtime showtime;
    private LocalDateTime startTime;
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
    @Column(precision = 10, scale = 2)
    private BigDecimal totalPrice;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist
    public void prePersist(){
        this.code = UuidCodeGenerator.generateCode("odr_");
        this.status = OrderStatus.PENDING_PAYMENT;
    }

}
