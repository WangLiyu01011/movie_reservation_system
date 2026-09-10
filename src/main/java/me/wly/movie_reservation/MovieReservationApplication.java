package me.wly.movie_reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
//import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication
@EnableScheduling
public class MovieReservationApplication {

	public static void main(String[] args) {
		SpringApplication.run(MovieReservationApplication.class, args);
	}

}
