package me.wly.movie_reservation.showtime.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public record ShowtimeSearchDTO(
        @NotNull @Positive Integer movieId,
        @NotNull @Positive Integer cityId,
        @Positive Integer districtId,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startFrom,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTo,
        @Min(1) Integer minAvailableSeats, // 最小可用座位数
        @Min(0) Integer page, // 页数，从0开始算
        @Min(1) @Max(20) Integer size // 指定的每页数量
) {
    public ShowtimeSearchDTO {
        minAvailableSeats = minAvailableSeats == null ? 0 : minAvailableSeats;
        page = page == null ? 0 : page;
        size = size == null ? 10 : size;
    }
}
