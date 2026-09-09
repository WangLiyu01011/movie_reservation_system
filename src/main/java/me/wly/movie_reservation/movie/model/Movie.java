package me.wly.movie_reservation.movie.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;


@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String imdbId;
    @JsonFormat(pattern = "yyyy年MM月dd日", timezone = "GMT+8")
    private LocalDateTime releaseDate;
    @JsonFormat(pattern = "yyyy年MM月dd日", timezone = "GMT+8")
    private LocalDateTime offDate;
    private String title;
    private String description;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> genres;
    private String language;
    private String posterImageURL;
}
