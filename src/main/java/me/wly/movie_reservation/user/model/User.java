package me.wly.movie_reservation.user.model;

import jakarta.persistence.*;
import lombok.*;
import me.wly.movie_reservation.common.util.UuidCodeGenerator;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.theater.model.TheaterAdmin;

import java.util.*;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_user_email_address", columnNames = "email_address"),
                @UniqueConstraint(name = "uk_user_phone_number", columnNames = "phone_number")
        }
)
public class User {
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 80, nullable = false)
    private String code;
    @Column(length = 32, nullable = false, columnDefinition = "char(32)")
    private String username;
    @Column(length = 48, nullable = false)
    private String nickname;
    @Column(name = "email_address", length = 60)
    private String emailAddress;
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    @Column(length = 80, nullable = false)
    private String password;
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", length = 32, nullable = false)
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
