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
public class Area {
    @Id
    private int id;
    @Column(length = 32, nullable = false, columnDefinition = "char(32)")
    private String name;
    @Column(name = "parent_id", nullable = false)
    private int parentId;
    @Column(nullable = false)
    private short level; // 0 for province , 1 for cities, 2 for district or counties

}
