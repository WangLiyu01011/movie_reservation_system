package me.wly.movie_reservation.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.api.ApiResponse;
import me.wly.movie_reservation.payment.dto.PaymentRequestDTO;
import me.wly.movie_reservation.payment.dto.RefundRequestDTO;
import me.wly.movie_reservation.payment.model.PaymentStatus;
import me.wly.movie_reservation.payment.model.PaymentTaskType;
import me.wly.movie_reservation.payment.vo.RefundVO;
import me.wly.movie_reservation.payment.vo.PaymentRequestVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final PaymentRefundService refunds;
    private final PaymentCompensationWorker worker;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentRequestVO>> createPayment(
            @Valid @RequestBody PaymentRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        PaymentRequestVO payment = paymentService.createPayment(dto, userDetails.getUsername());
        return new ResponseEntity<>(ApiResponse.success(payment), payment.status() == PaymentStatus.CREATED
                ? HttpStatus.ACCEPTED : HttpStatus.CREATED);
    }

    @GetMapping("/{paymentNo}")
    public ApiResponse<PaymentRequestVO> getPayment(@PathVariable String paymentNo,
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success(paymentService.getPayment(paymentNo, user.getUsername()));
    }

    @PostMapping("/{paymentNo}/query")
    public ApiResponse<PaymentRequestVO> queryPayment(@PathVariable String paymentNo,
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success(paymentService.queryPayment(paymentNo, user.getUsername()));
    }

    @PostMapping("/{paymentNo}/refunds")
    public ApiResponse<RefundVO> refund(@PathVariable String paymentNo, @Valid @RequestBody RefundRequestDTO dto,
            @AuthenticationPrincipal UserDetails user) {
        refunds.request(paymentNo, dto, user.getUsername());
        worker.runFor(paymentNo, PaymentTaskType.REFUND);
        return ApiResponse.success(refunds.ownedRefund(paymentNo, user.getUsername()));
    }

    @GetMapping("/{paymentNo}/refunds")
    public ApiResponse<RefundVO> refundStatus(@PathVariable String paymentNo,
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success(refunds.ownedRefund(paymentNo, user.getUsername()));
    }
}
