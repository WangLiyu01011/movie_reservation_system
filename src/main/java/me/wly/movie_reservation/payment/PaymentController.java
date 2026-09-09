package me.wly.movie_reservation.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.api.ApiResponse;
import me.wly.movie_reservation.payment.dto.OrderPayDTO;
import me.wly.movie_reservation.payment.vo.OrderPayVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderPayVO>> createPayment(
            @Valid @RequestBody OrderPayDTO dto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        OrderPayVO payment = paymentService.createPayment(dto, userDetails.getUsername());
        return new ResponseEntity<>(ApiResponse.success(payment), HttpStatus.CREATED);
    }
}
