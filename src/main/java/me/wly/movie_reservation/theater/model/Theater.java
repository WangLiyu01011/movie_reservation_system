package me.wly.movie_reservation.theater.model;

import jakarta.persistence.*;
import lombok.*;


import java.util.ArrayList;
import java.util.List;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Theater {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne
    @JoinColumn(name = "city_id", referencedColumnName = "id")
    private Area city;
    @ManyToOne
    @JoinColumn(name = "district_id", referencedColumnName = "id")
    private Area district;
    private String theaterName;
    private String location;

    @OneToMany(cascade = CascadeType.ALL,
               fetch = FetchType.EAGER,
               mappedBy = "theater",
               orphanRemoval = true
    )
    private List<Hall> Halls= new ArrayList<>();

}
