package me.wly.movie_reservation.model.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.wly.movie_reservation.common.utils.UuidCodeGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne
    @JoinColumn(name = "movie_id")
    private Movie movie;
    private String movieTitle;
    @ManyToOne
    @JoinColumn(name = "showtime_id")
    private Showtime showtime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @OneToMany
    @JoinColumn(name = "seat_id")
    private List<Seat> seat;
    private List<String> seatLocations;
    private BigDecimal totalPrice;
    @PrePersist
    public void prePersist(){
        this.code = UuidCodeGenerator.generateCode("odr_");
    }

}
