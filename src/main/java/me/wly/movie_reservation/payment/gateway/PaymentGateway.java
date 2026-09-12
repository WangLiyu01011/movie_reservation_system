package me.wly.movie_reservation.payment.gateway;

public interface PaymentGateway {
    /**
     * The channel code accepted by the payment API, for example MOCK, ALIPAY or WECHAT.
     */
    String channel();

    /**
     * Creates a payment order on the selected provider.
     *
     * <p>This method only communicates with the provider. It must not update Order,
     * PaymentTransaction or ShowtimeSeat entities.</p>
     */
    PaymentCreateResult createPayment(PaymentCreateCommand command);

    /** Verifies a provider callback signature and converts its body into a provider-neutral event. */
    VerifiedPaymentCallback verifyAndParseCallback(String rawBody, String signature);
}
