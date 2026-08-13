package me.wly.movie_reservation.model.entity;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;
import me.wly.movie_reservation.UserRole;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    private Integer id;
    private String username;
    private String nickName;
    private String emailAddress;
    private String phoneNumber;
    private String password;
    @EnumeratedValue
    private UserRole userRole;
}
