package me.wly.movie_reservation.payment;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.payment.dto.PaymentRequestDTO;
import me.wly.movie_reservation.payment.gateway.PaymentGatewayRegistry;
import me.wly.movie_reservation.payment.model.PaymentTaskType;
import me.wly.movie_reservation.payment.vo.PaymentRequestVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PaymentService {
    private final PaymentGatewayRegistry gateways;
    private final PaymentLifecycleService lifecycle;
    private final PaymentCompensationWorker worker;

    public PaymentRequestVO createPayment(PaymentRequestDTO dto, String username) {
        String channel = gateways.getGateway(dto.channel()).channel();
        String paymentNo = lifecycle.prepare(dto, username, channel);
        worker.runFor(paymentNo, PaymentTaskType.SYNC);
        return lifecycle.ownedPayment(paymentNo, username);
    }

    public PaymentRequestVO getPayment(String paymentNo, String username) {
        return lifecycle.ownedPayment(paymentNo, username);
    }

    public PaymentRequestVO queryPayment(String paymentNo, String username) {
        lifecycle.ownedPayment(paymentNo, username);
        worker.runFor(paymentNo, PaymentTaskType.SYNC);
        return lifecycle.ownedPayment(paymentNo, username);
    }
}
