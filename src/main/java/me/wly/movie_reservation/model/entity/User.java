package me.wly.movie_reservation.model.entity;

import jakarta.persistence.*;
import lombok.*;
import me.wly.movie_reservation.common.utils.UuidCodeGenerator;
import me.wly.movie_reservation.model.enum_class.UserRole;

import java.util.*;


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
    private String nickname;
    private String emailAddress;
    private String phoneNumber;
    private String password;
    @Enumerated(EnumType.STRING)
    private UserRole userRole;
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TheaterAdmin> theaterAdmins = new HashSet<>();
    @OneToMany(mappedBy = "user" , fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Order> orders = new ArrayList<>();

    @PrePersist
    public void prePersist(){
        this.code = UuidCodeGenerator.generateCode("usr_");
        if (this.nickname == null || this.nickname.trim().isEmpty()) {
            String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
            this.nickname = "user_" + randomSuffix;
        }
    }

    public void addOrder(Order order) {
        orders.add(order);
        order.setUser(this);
    }

    public void removeOrder(Order order) {
        orders.remove(order);
        order.setUser(null);
    }

}
