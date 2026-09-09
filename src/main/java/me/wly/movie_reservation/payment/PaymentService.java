package me.wly.movie_reservation.payment;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.order.OrderRepository;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderStatus;
import me.wly.movie_reservation.payment.dto.OrderPayDTO;
import me.wly.movie_reservation.payment.model.PaymentStatus;
import me.wly.movie_reservation.payment.model.PaymentTransaction;
import me.wly.movie_reservation.payment.vo.OrderPayVO;
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

    @Transactional(noRollbackFor = BusinessException.class)
    public OrderPayVO createPayment(OrderPayDTO dto, String username) {
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
            return toOrderPayVO(sameRequest);
        }

        PaymentTransaction activePayment = paymentTransactionRepository
                .findFirstByOrder_IdAndStatusInOrderByIdDesc(order.getId(), ACTIVE_PAYMENT_STATUSES)
                .orElse(null);
        if (activePayment != null) {
            return toOrderPayVO(activePayment);
        }

        PaymentTransaction payment = new PaymentTransaction();
        payment.setOrder(order);
        payment.setRequestId(dto.requestId());
        payment.setChannel(dto.channel().trim().toUpperCase());
        payment.setAmount(order.getTotalPrice());
        payment.setStatus(PaymentStatus.CREATED);

        return toOrderPayVO(paymentTransactionRepository.save(payment));
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

    private OrderPayVO toOrderPayVO(PaymentTransaction payment) {
        return new OrderPayVO(
                payment.getPaymentNo(),
                payment.getOrder().getCode(),
                payment.getChannel(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getOrder().getExpiresAt()
        );
    }
}
