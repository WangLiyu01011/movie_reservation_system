package me.wly.movie_reservation.ai.vo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import me.wly.movie_reservation.ai.protocol.AiMissingField;
import me.wly.movie_reservation.showtime.dto.ShowtimeCandidateDTO;

import java.util.List;
import java.util.Objects;

/** 按照Type进行渲染的有序的前端UI组件， */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AiBlock.Text.class, name = "TEXT"),
        @JsonSubTypes.Type(value = AiBlock.MovieCard.class, name = "MOVIE_CARD"),
        @JsonSubTypes.Type(value = AiBlock.ShowtimeCard.class, name = "SHOWTIME_CARD"),
        @JsonSubTypes.Type(value = AiBlock.InputRequest.class, name = "INPUT_REQUEST"),
        @JsonSubTypes.Type(value = AiBlock.Notice.class, name = "NOTICE")
})
public sealed interface AiBlock permits AiBlock.Text, AiBlock.MovieCard,
        AiBlock.ShowtimeCard, AiBlock.InputRequest, AiBlock.Notice {

    String id();

    record Text(String id, TextData data) implements AiBlock {
        public Text {
            Objects.requireNonNull(id);
            Objects.requireNonNull(data);
        }
    }

    record MovieCard(String id, MovieCardData data) implements AiBlock {
        public MovieCard {
            Objects.requireNonNull(id);
            Objects.requireNonNull(data);
        }
    }

    record ShowtimeCard(String id, ShowtimeCardData data) implements AiBlock {
        public ShowtimeCard {
            Objects.requireNonNull(id);
            Objects.requireNonNull(data);
        }
    }

    record InputRequest(String id, InputRequestData data) implements AiBlock {
        public InputRequest {
            Objects.requireNonNull(id);
            Objects.requireNonNull(data);
        }
    }

    record Notice(String id, NoticeData data) implements AiBlock {
        public Notice {
            Objects.requireNonNull(id);
            Objects.requireNonNull(data);
        }
    }

    record TextData(String text) {
        public TextData {
            Objects.requireNonNull(text);
        }
    }

    record MovieCardData(ShowingMovieVO movie, String reason) {
        public MovieCardData {
            Objects.requireNonNull(movie);
            Objects.requireNonNull(reason);
        }
    }

    record ShowtimeCardData(ShowtimeCandidateDTO showtime, String reason) {
        public ShowtimeCardData {
            Objects.requireNonNull(showtime);
            Objects.requireNonNull(reason);
        }
    }

    record InputRequestData(List<AiMissingField> missingFields) {
        public InputRequestData {
            missingFields = List.copyOf(missingFields);
            if (missingFields.isEmpty()) {
                throw new IllegalArgumentException("Input request needs missing fields");
            }
        }
    }

    record NoticeData(String text) {
        public NoticeData {
            Objects.requireNonNull(text);
        }
    }
}
