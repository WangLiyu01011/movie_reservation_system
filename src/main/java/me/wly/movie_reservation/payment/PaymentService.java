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
import me.wly.movie_reservation.payment.gateway.MockPaymentGateway;
import me.wly.movie_reservation.payment.gateway.PaymentCreateCommand;
import me.wly.movie_reservation.payment.gateway.PaymentCreateResult;
import me.wly.movie_reservation.payment.model.PaymentStatus;
import me.wly.movie_reservation.payment.model.PaymentTransaction;
import me.wly.movie_reservation.payment.vo.PaymentRequestVO;
import me.wly.movie_reservation.user.UserRepository;
import me.wly.movie_reservation.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final List<PaymentStatus> ACTIVE_PAYMENT_STATUSES =
            List.of(PaymentStatus.CREATED, PaymentStatus.PAYING);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderExpirationService orderExpirationService;
    private final MockPaymentGateway mockPaymentGateway;
    private final EntityManager entityManager;

    @Transactional(noRollbackFor = BusinessException.class)
    public PaymentRequestVO createPayment(PaymentRequestDTO dto, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "User not found"));

        // 获取对应订单并上锁
        Order order = orderRepository.findByCodeAndUserIdForUpdate(dto.orderCode(), user.getId())
                .orElseThrow(() -> new BusinessException(ResultCode.ORDER_NOT_FOUND, "Order not found"));

        LocalDateTime now = LocalDateTime.now();
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
            return toPaymentRequestVO(sameRequest);
        }

        PaymentTransaction activePayment = paymentTransactionRepository
                .findFirstByOrder_IdAndStatusInOrderByIdDesc(order.getId(), ACTIVE_PAYMENT_STATUSES)
                .orElse(null);
        if (activePayment != null) {
            return toPaymentRequestVO(activePayment);
        }

        PaymentTransaction payment = new PaymentTransaction();
        payment.setOrder(order);
        payment.setRequestId(dto.requestId());
        payment.setChannel(dto.channel().trim().toUpperCase());
        payment.setAmount(order.getTotalPrice());
        payment.setStatus(PaymentStatus.CREATED);
        PaymentTransaction saved = paymentTransactionRepository.saveAndFlush(payment);
        entityManager.refresh(saved);

        PaymentCreateResult result = mockPaymentGateway.createPayment(createCommand(saved, order));
        saved.setProviderTradeNo(result.providerTradeNo());
        saved.setPayUrl(result.payUrl());
        saved.setStatus(PaymentStatus.PAYING);

        return toPaymentRequestVO(saved);
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
                "/api/v1/payment-callbacks/mock/"
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
