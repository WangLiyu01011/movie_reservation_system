package me.wly.movie_reservation.ai.dto;

import dev.langchain4j.model.output.structured.Description;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import me.wly.movie_reservation.ai.protocol.AiMissingField;
import me.wly.movie_reservation.ai.protocol.AiResponseType;
import java.util.List;

/** Model-only selection; the server builds public blocks from current tool evidence. */
public record AiAnswerDTO(
        @NotNull AiResponseType type,
        @NotBlank @Size(max = 2000) String message,
        @Description("推荐电影，只能引用本次电影工具结果中的ID，没有则返回空数组")
        @NotNull @Size(max = 20) List<@NotNull @Valid MovieRecommendation> movies,
        @Description("推荐场次，只能引用本次场次工具结果中的ID，没有则返回空数组")
        @NotNull @Size(max = 20) List<@NotNull @Valid ShowtimeRecommendation> showtimes,
        @Description("仍需补充的必要条件，无需补充时返回空数组；行政区不是必填条件")
        @NotNull List<@NotNull AiMissingField> missingFields
) {
    public record MovieRecommendation(@NotNull @Positive Integer movieId,
                                      @NotBlank @Size(max = 500) String reason) {}
    public record ShowtimeRecommendation(@NotNull @Positive Long showtimeId,
                                         @NotBlank @Size(max = 500) String reason) {}
}
