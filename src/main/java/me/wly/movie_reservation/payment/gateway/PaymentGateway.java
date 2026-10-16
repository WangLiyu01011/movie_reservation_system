package me.wly.movie_reservation.payment.gateway;

public abstract class PaymentGateway {
    /**
     * The channel code accepted by the payment API, for example MOCK, ALIPAY or WECHAT.
     */
    public abstract String channel();

    /**
     * Creates a payment order on the selected provider.
     *
     * <p>This method only communicates with the provider. It must not update Order,
     * PaymentTransaction or ShowtimeSeat entities.</p>
     */
    public abstract PaymentCreateResult createPayment(PaymentCreateCommand command);

    /** Verifies a provider callback signature and converts its body into a provider-neutral event. */
    public abstract VerifiedPaymentCallback verifyAndParseCallback(String rawBody, String signature);
}
