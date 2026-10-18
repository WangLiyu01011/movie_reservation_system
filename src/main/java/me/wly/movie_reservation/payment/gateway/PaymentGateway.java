package me.wly.movie_reservation.payment.gateway;

public abstract class PaymentGateway {
    /**
     * The channel code accepted by the payment API, for example MOCK, ALIPAY or WECHAT.
     */
    public abstract String channel();

    /**
     * Creates a payment order on the selected provider, idempotently by command.paymentNo().
     *
     * <p>This method only communicates with the provider. It must not update Order,
     * PaymentTransaction or ShowtimeSeat entities.</p>
     */
    public abstract PaymentCreateResult createPayment(PaymentCreateCommand command);

    /** Query by merchant paymentNo, including when a create response was lost. */
    public abstract PaymentQueryResult queryPayment(String paymentNo);

    /** Atomically closes an unpaid provider order, or returns its actual final state. */
    public abstract PaymentQueryResult closePayment(String paymentNo);

    /** Must be idempotent by command.refundNo(), including after a lost success response. */
    public abstract RefundQueryResult refund(RefundCommand command);

    public abstract RefundQueryResult queryRefund(String refundNo);

    /** Verifies a provider callback signature and converts its body into a provider-neutral event. */
    public abstract VerifiedPaymentCallback verifyAndParseCallback(String rawBody, String signature);
}
