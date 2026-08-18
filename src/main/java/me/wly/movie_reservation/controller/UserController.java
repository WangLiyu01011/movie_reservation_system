package me.wly.movie_reservation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.utils.ApiResponse;
import me.wly.movie_reservation.model.dto.UserRegisterDTO;
import me.wly.movie_reservation.model.vo.UserVO;
import me.wly.movie_reservation.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    @PostMapping(path = "/register")
    public ResponseEntity<ApiResponse<UserVO>> generateCustomer(@Valid @RequestBody UserRegisterDTO newUser) {
        UserVO res = userService.createCustomerUser(newUser);
        return new ResponseEntity<ApiResponse<UserVO>>(ApiResponse.success(res), HttpStatus.CREATED);
    }
}
