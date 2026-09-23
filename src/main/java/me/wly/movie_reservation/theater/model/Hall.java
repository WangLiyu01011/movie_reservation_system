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
@Table(
        name = "hall",
        uniqueConstraints = @UniqueConstraint(name = "uk_hall_theater_name", columnNames = {"theater_id", "name"})
)
public class Hall {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;
    @Column(length = 40, nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false, columnDefinition = "char(20)")
    private HallType type;
    @Column(length = 16, nullable = false)
    private String status;
    @Column(name = "row_count", nullable = false)
    private Integer rowCount;
    @Column(name = "column_count", nullable = false)
    private Integer columnCount;


}
