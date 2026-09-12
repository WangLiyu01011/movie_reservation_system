package me.wly.movie_reservation.payment;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.payment.gateway.MockPaymentGateway;
import me.wly.movie_reservation.payment.gateway.VerifiedPaymentCallback;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-callbacks")
@RequiredArgsConstructor
public class PaymentCallbackController {
    private final PaymentCallbackService paymentCallbackService;
    private final MockPaymentGateway mockPaymentGateway;

    @PostMapping(
            path = "/mock",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> mockCallback(
            @RequestBody String rawBody,
            @RequestHeader("X-Mock-Signature") String signature
    ) {
        VerifiedPaymentCallback callback = mockPaymentGateway.verifyAndParseCallback(rawBody, signature);
        paymentCallbackService.handleCallback(callback);
        return ResponseEntity.ok("success");
    }
}
