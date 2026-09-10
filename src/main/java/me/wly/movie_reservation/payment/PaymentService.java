package me.wly.movie_reservation.payment;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
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
import me.wly.movie_reservation.showtime.ShowtimeSeatRepository;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import me.wly.movie_reservation.user.UserRepository;
import me.wly.movie_reservation.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final MockPaymentGateway mockPaymentGateway;
    private final EntityManager entityManager;

    @Transactional
    public PaymentRequestVO createPayment(PaymentRequestDTO dto, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "User not found"));
        Order order = orderRepository.findByCodeAndUserIdForUpdate(dto.orderCode(), user.getId())
                .orElseThrow(() -> new BusinessException(ResultCode.ORDER_NOT_FOUND, "Order not found"));

        LocalDateTime now = LocalDateTime.now();
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                && (order.getExpiresAt() == null || !order.getExpiresAt().isAfter(now))) {
            expireOrder(order, now);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "Order cannot be paid in status: " + order.getStatus()
            );
        }
        if (order.getTotalPrice() == null || order.getTotalPrice().signum() < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Order amount is invalid");
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

    private void expireOrder(Order order, LocalDateTime now) {
        order.setStatus(OrderStatus.EXPIRED);
        order.setCancelledAt(now);
        order.setUpdatedAt(now);

        List<ShowtimeSeat> lockedSeats = showtimeSeatRepository.findLockedByOrderIdForUpdate(order.getId());
        for (ShowtimeSeat seat : lockedSeats) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setOrder(null);
            seat.setLockToken(null);
            seat.setLockUntil(null);
        }

        List<PaymentTransaction> activePayments = paymentTransactionRepository.findAllByOrder_IdAndStatusIn(
                order.getId(), ACTIVE_PAYMENT_STATUSES
        );
        activePayments.forEach(payment -> payment.setStatus(PaymentStatus.CLOSED));
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
