package me.wly.movie_reservation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.utils.ApiResponse;
import me.wly.movie_reservation.model.dto.OrderGenerateDTO;
import me.wly.movie_reservation.model.dto.UserOrderDTO;
import me.wly.movie_reservation.model.vo.OrderVO;
import me.wly.movie_reservation.service.MyUserDetailsService;
import me.wly.movie_reservation.service.OrderService;
import me.wly.movie_reservation.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    @GetMapping("/{userCode}")
    public ResponseEntity<ApiResponse<List<OrderVO>>> getOrders(@PathVariable String userCode) {
        return new ResponseEntity<ApiResponse<List<OrderVO>>>(ApiResponse.success(orderService.getOrderByUserCode(userCode)), HttpStatus.OK);
    }
    @PostMapping
    public ResponseEntity<ApiResponse<String>> generateOrder(@Valid @RequestBody OrderGenerateDTO dto, @AuthenticationPrincipal UserDetails userDetails) {
        String orderCode = orderService.generateOrder(dto, userDetails.getUsername());
        return new ResponseEntity<>(ApiResponse.success(orderCode), HttpStatus.CREATED);
    }
}
