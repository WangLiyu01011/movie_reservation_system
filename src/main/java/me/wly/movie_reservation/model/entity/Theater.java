package me.wly.movie_reservation.model.entity;

import jakarta.persistence.*;
import lombok.*;
import me.wly.movie_reservation.model.enum_class.HallType;
import org.springframework.data.repository.cdi.Eager;


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
