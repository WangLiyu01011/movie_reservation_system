package me.wly.movie_reservation.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<T>(200, "success", data);
    }
}
