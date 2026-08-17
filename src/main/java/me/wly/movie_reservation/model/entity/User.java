package me.wly.movie_reservation.model.entity;

import jakarta.persistence.*;
import lombok.*;
import me.wly.movie_reservation.common.utils.generateUuidCode;
import me.wly.movie_reservation.model.enum_class.UserRole;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Integer id;
    private String code;
    private String username;
    private String nickName;
    private String emailAddress;
    private String phoneNumber;
    private String password;
    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    @PrePersist
    public void perPersist(){
        this.code = generateUuidCode.setCode("usr_");
    }
}
