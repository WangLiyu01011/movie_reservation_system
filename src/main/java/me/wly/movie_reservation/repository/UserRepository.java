package me.wly.movie_reservation.repository;

import me.wly.movie_reservation.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmailAddress(String emailAddress);
    Optional<User> findByUsername(String username);
    User getUserByCode(String code);
    User getUserByUsername(String username);
}
