package me.wly.movie_reservation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.utils.ApiResponse;
import me.wly.movie_reservation.common.utils.JwtUtil;
import me.wly.movie_reservation.model.dto.UserLoginDTO;
import me.wly.movie_reservation.model.dto.UserRegisterDTO;
import me.wly.movie_reservation.model.vo.LoginResultVO;
import me.wly.movie_reservation.model.vo.UserLoginVO;
import me.wly.movie_reservation.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping(path = "/customer/register")
    public ResponseEntity<ApiResponse<UserLoginVO>> generateCustomer(@Valid @RequestBody UserRegisterDTO newUser) {
        UserLoginVO res = userService.customerCreate(newUser);
        return new ResponseEntity<ApiResponse<UserLoginVO>>(ApiResponse.success(res), HttpStatus.CREATED);
    }

    @PostMapping(path = "/customer/login")
    public ResponseEntity<ApiResponse<LoginResultVO>> customerLogin(@Valid @RequestBody UserLoginDTO userLoginDTO) {
        JwtUtil.TokenResult tokenResult = userService.authenticateAndGetToken(userLoginDTO);
        UserLoginVO userInfo = userService.getUserInfo(userLoginDTO);
        List<String> roles = new ArrayList<String>();
        roles.add("Customer");
        List<String> permissions = new ArrayList<>();
        LoginResultVO result = new LoginResultVO(tokenResult.token(),"Bearer ", tokenResult.expireTime(), userInfo, roles, permissions);
        return new ResponseEntity<ApiResponse<LoginResultVO>>(ApiResponse.success(result), HttpStatus.OK);
    }
}
