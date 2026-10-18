package me.wly.movie_reservation.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.api.ApiResponse;
import me.wly.movie_reservation.payment.dto.MockPaymentCompleteDTO;
import me.wly.movie_reservation.payment.gateway.*;
import me.wly.movie_reservation.payment.vo.PaymentRequestVO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/** Local provider simulator. The caller must own the merchant payment. */
@RestController @RequestMapping("/api/v1/mock-payments") @RequiredArgsConstructor
public class MockCheckoutController {
    private final PaymentLifecycleService lifecycle;
    private final MockProviderService provider;
    private final PaymentCallbackService callbacks;

    @GetMapping("/{paymentNo}")
    public ApiResponse<PaymentQueryResult> checkout(@PathVariable String paymentNo, @AuthenticationPrincipal UserDetails user) {
        lifecycle.ownedPayment(paymentNo, user.getUsername());
        return ApiResponse.success(provider.query(paymentNo));
    }

    @PostMapping("/{paymentNo}/complete")
    public ApiResponse<PaymentRequestVO> complete(@PathVariable String paymentNo,
            @Valid @RequestBody MockPaymentCompleteDTO dto, @AuthenticationPrincipal UserDetails user) {
        lifecycle.ownedPayment(paymentNo, user.getUsername());
        VerifiedPaymentCallback callback = provider.complete(paymentNo, dto.status());
        if (dto.notifyMerchant()) callbacks.handleCallback(callback);
        return ApiResponse.success(lifecycle.ownedPayment(paymentNo, user.getUsername()));
    }
}
