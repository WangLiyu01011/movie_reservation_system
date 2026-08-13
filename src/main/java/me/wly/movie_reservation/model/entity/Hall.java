package me.wly.movie_reservation.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.wly.movie_reservation.HallType;


@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Hall {
    @Id
    private Integer id;
    @ManyToOne
    @JoinColumn(name = "theater_id")
    private Theater theater;
    private HallType type;
}
