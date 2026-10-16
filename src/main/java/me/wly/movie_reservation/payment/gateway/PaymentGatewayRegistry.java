package me.wly.movie_reservation.payment.gateway;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.payment.model.AvailablePaymentChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Routes a channel to its Spring-managed gateway; never constructs provider clients itself. */
@Component
public class PaymentGatewayRegistry {
    private final Map<AvailablePaymentChannel, PaymentGateway> gateways;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        Map<AvailablePaymentChannel, PaymentGateway> registered = new EnumMap<>(AvailablePaymentChannel.class);
        for (PaymentGateway gateway : gateways) {
            AvailablePaymentChannel channel;
            try {
                channel = AvailablePaymentChannel.valueOf(gateway.channel());
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new IllegalStateException("Gateway must declare a supported canonical channel", exception);
            }
            if (registered.putIfAbsent(channel, gateway) != null) {
                throw new IllegalStateException("Multiple payment gateways registered for channel: " + channel);
            }
        }
        this.gateways = Map.copyOf(registered);
    }

    public PaymentGateway getGateway(String channelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Payment channel is required");
        }
        AvailablePaymentChannel channel;
        try {
            channel = AvailablePaymentChannel.valueOf(channelCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Unsupported payment channel");
        }
        PaymentGateway gateway = gateways.get(channel);
        if (gateway == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Payment channel is not available");
        }
        return gateway;
    }
}
