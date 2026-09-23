package me.wly.movie_reservation.order;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.api.ApiResponse;
import me.wly.movie_reservation.order.dto.OrderCreateDTO;
import me.wly.movie_reservation.order.vo.OrderCreateVO;
import me.wly.movie_reservation.order.vo.OrderVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    @GetMapping()
    public ResponseEntity<ApiResponse<List<OrderVO>>> getOrders(@AuthenticationPrincipal UserDetails userDetails) {
        return new ResponseEntity<>(ApiResponse.success(orderService.getOrderByUsername(userDetails.getUsername())), HttpStatus.OK);
    }


    @PostMapping
    public ResponseEntity<ApiResponse<OrderCreateVO>> createOrder(@Valid @RequestBody OrderCreateDTO dto, @AuthenticationPrincipal UserDetails userDetails) {
        OrderCreateVO orderCreateVO = orderService.createOrder(dto, userDetails.getUsername());
        return new ResponseEntity<>(ApiResponse.success(orderCreateVO), HttpStatus.CREATED);
    }

}
