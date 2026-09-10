package me.wly.movie_reservation.payment.gateway;

import me.wly.movie_reservation.common.util.UuidCodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class MockPaymentGateway implements PaymentGateway {
    private static final String CHANNEL = "MOCK";

    private final String checkoutBaseUrl;

    public MockPaymentGateway(
            @Value("${payment.mock.checkout-base-url:http://localhost:8080/api/v1/mock-payments}")
            String checkoutBaseUrl
    ) {
        this.checkoutBaseUrl = checkoutBaseUrl;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public PaymentCreateResult createPayment(PaymentCreateCommand command) {
        String providerTradeNo = UuidCodeGenerator.generateCode("mock_trade_");
        String payUrl = UriComponentsBuilder.fromUriString(checkoutBaseUrl)
                .pathSegment(command.paymentNo())
                .build()
                .toUriString();

        return new PaymentCreateResult(
                providerTradeNo,
                payUrl,
                command.expiresAt()
        );
    }
}
