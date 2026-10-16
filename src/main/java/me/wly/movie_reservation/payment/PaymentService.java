package me.wly.movie_reservation.payment;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.order.OrderExpirationService;
import me.wly.movie_reservation.order.OrderRepository;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderStatus;
import me.wly.movie_reservation.payment.dto.PaymentRequestDTO;
import me.wly.movie_reservation.payment.gateway.PaymentCreateCommand;
import me.wly.movie_reservation.payment.gateway.PaymentCreateResult;
import me.wly.movie_reservation.payment.gateway.PaymentGateway;
import me.wly.movie_reservation.payment.gateway.PaymentGatewayRegistry;
import me.wly.movie_reservation.payment.model.PaymentStatus;
import me.wly.movie_reservation.payment.model.PaymentTransaction;
import me.wly.movie_reservation.payment.vo.PaymentRequestVO;
import me.wly.movie_reservation.user.UserRepository;
import me.wly.movie_reservation.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final Clock businessClock;
    private static final List<PaymentStatus> ACTIVE_PAYMENT_STATUSES =
            List.of(PaymentStatus.CREATED, PaymentStatus.PAYING);
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderExpirationService orderExpirationService;
    private final PaymentGatewayRegistry paymentGatewayRegistry;
    private final EntityManager entityManager;

    @Transactional(noRollbackFor = BusinessException.class)
    public PaymentRequestVO createPayment(PaymentRequestDTO dto, String username) {
        PaymentGateway paymentGateway = paymentGatewayRegistry.getGateway(dto.channel());
        String channel = paymentGateway.channel();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "User not found"));

        // 获取对应订单并上锁
        Order order = orderRepository.findByCodeAndUserIdForUpdate(dto.orderCode(), user.getId())
                .orElseThrow(() -> new BusinessException(ResultCode.ORDER_NOT_FOUND, "Order not found"));

        LocalDateTime now = LocalDateTime.now(businessClock);
        if (orderExpirationService.expireLockedOrder(order, now)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Order has expired");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "Order cannot be paid in status: " + order.getStatus()
            );
        }

        if (order.getTotalPrice() == null
                || order.getTotalPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "Order price cannot be negative or null"
            );
        }

        PaymentTransaction sameRequest = paymentTransactionRepository
                .findByOrder_IdAndRequestId(order.getId(), dto.requestId())
                .orElse(null);
        if (sameRequest != null) {
            requireSameChannel(sameRequest, channel);
            return toPaymentRequestVO(sameRequest);
        }

        PaymentTransaction activePayment = paymentTransactionRepository
                .findFirstByOrder_IdAndStatusInOrderByIdDesc(order.getId(), ACTIVE_PAYMENT_STATUSES)
                .orElse(null);
        if (activePayment != null) {
            requireSameChannel(activePayment, channel);
            return toPaymentRequestVO(activePayment);
        }

        PaymentTransaction payment = new PaymentTransaction();
        payment.setOrder(order);
        payment.setRequestId(dto.requestId());
        payment.setChannel(channel);
        payment.setAmount(order.getTotalPrice());
        payment.setStatus(PaymentStatus.CREATED);
        PaymentTransaction saved = paymentTransactionRepository.saveAndFlush(payment);
        entityManager.refresh(saved);

        PaymentCreateResult result = paymentGateway.createPayment(createCommand(saved, order));
        saved.setProviderTradeNo(result.providerTradeNo());
        saved.setPayUrl(result.payUrl());
        saved.setStatus(PaymentStatus.PAYING);

        return toPaymentRequestVO(saved);
    }

    private void requireSameChannel(PaymentTransaction payment, String requestedChannel) {
        if (!requestedChannel.equals(payment.getChannel())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "An existing payment uses a different channel; payment channel cannot be changed");
        }
    }

    private PaymentRequestVO toPaymentRequestVO(PaymentTransaction payment) {
        return new PaymentRequestVO(
                payment.getPaymentNo(),
                payment.getOrder().getCode(),
                payment.getChannel(),
                payment.getAmount(),
                payment.getStatus(),
                getEffectiveExpiresAt(payment.getOrder(), payment),
                payment.getPayUrl()
        );
    }

    private PaymentCreateCommand createCommand(PaymentTransaction transaction, Order order) {
        return new PaymentCreateCommand(
                transaction.getPaymentNo(),
                order.getCode(),
                order.getTotalPrice(),
                order.getMovieTitle() + " " + order.getOrderSeats().size(),
                getEffectiveExpiresAt(order, transaction),
                "/api/v1/payment-callbacks/" + transaction.getChannel().toLowerCase(Locale.ROOT)
        );
    }

    private LocalDateTime getEffectiveExpiresAt(Order order, PaymentTransaction transaction) {
        LocalDateTime orderExpiresAt = order.getExpiresAt();
        LocalDateTime paymentExpiresAt = transaction.getExpiresAt();

        if (orderExpiresAt == null) {
            return paymentExpiresAt;
        }
        if (paymentExpiresAt == null) {
            return orderExpiresAt;
        }
        return orderExpiresAt.isBefore(paymentExpiresAt) ? orderExpiresAt : paymentExpiresAt;
    }
}
