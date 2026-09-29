package me.wly.movie_reservation.showtime.dto;

// 可用座位数量传输类型
public record ShowtimeSeatCountDTO(Long showtimeId, long availableSeatCount) {
}
