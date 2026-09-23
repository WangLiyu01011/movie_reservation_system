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
@Table(
        name = "movie",
        uniqueConstraints = @UniqueConstraint(name = "imdbId_UNIQUE", columnNames = "imdb_id"),
        indexes = @Index(name = "idx_movie_release_date", columnList = "release_date")
)
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "imdb_id", length = 12)
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
    @Column(length = 45)
    private String language;
    @Column(name = "poster_imageurl", length = 512)
    private String posterImageURL;
}
