package me.wly.movie_reservation.model.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.wly.movie_reservation.common.utils.UuidCodeGenerator;

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
    @ManyToOne
    @JoinColumn(name = "showtime_id")
    private Showtime showtime;
    @OneToOne
    @JoinColumn(name = "seat_id")
    private Seat seat;
    @PrePersist
    public void prePersist(){
        this.code = UuidCodeGenerator.generateCode("odr_");
    }

}
