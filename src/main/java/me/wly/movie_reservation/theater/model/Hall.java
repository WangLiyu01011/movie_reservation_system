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
public class Hall {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne
    @JoinColumn(name = "theater_id")
    private Theater theater;
    private String name;
    @Enumerated(EnumType.STRING)
    private HallType type;
    private String status;
    private Integer rowCount;
    private Integer columnCount;


}
