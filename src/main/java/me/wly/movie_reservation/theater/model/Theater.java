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
@Table(
        name = "theater",
        uniqueConstraints = @UniqueConstraint(name = "uk_theater_district_name", columnNames = {"district_id", "theater_name"}),
        indexes = @Index(name = "idx_city_district", columnList = "city_id,district_id")
)
public class Theater {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne
    @JoinColumn(name = "city_id", referencedColumnName = "id", nullable = false)
    private Area city;
    @ManyToOne
    @JoinColumn(name = "district_id", referencedColumnName = "id", nullable = false)
    private Area district;
    @Column(name = "theater_name", length = 100, nullable = false)
    private String theaterName;
    @Column(length = 255, nullable = false)
    private String location;

    @OneToMany(cascade = CascadeType.ALL,
               fetch = FetchType.LAZY,
               mappedBy = "theater",
               orphanRemoval = true
    )
    private List<Hall> Halls= new ArrayList<>();

}
