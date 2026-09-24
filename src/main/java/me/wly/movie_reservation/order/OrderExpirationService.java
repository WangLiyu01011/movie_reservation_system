package me.wly.movie_reservation.order;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderStatus;
import me.wly.movie_reservation.payment.PaymentTransactionRepository;
import me.wly.movie_reservation.payment.model.PaymentStatus;
import me.wly.movie_reservation.payment.model.PaymentTransaction;
import me.wly.movie_reservation.showtime.ShowtimeSeatRepository;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderExpirationService {
    private static final List<PaymentStatus> ACTIVE_PAYMENT_STATUSES =
            List.of(PaymentStatus.CREATED, PaymentStatus.PAYING);

    private final OrderRepository orderRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    /** 创建独立的新事务处理单个对象，确保不会导致一整个批次回滚 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderExpirationResult expireOne(Long orderId, LocalDateTime now) {
        return orderRepository.findByIdForUpdate(orderId)
                .map(order -> {
                    if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                        return OrderExpirationResult.ALREADY_TERMINAL;
                    }
                    if (order.getExpiresAt() != null && order.getExpiresAt().isAfter(now)) {
                        return OrderExpirationResult.NOT_DUE;
                    }

                    expireLockedOrder(order, now);
                    return OrderExpirationResult.EXPIRED;
                })
                .orElse(OrderExpirationResult.NOT_FOUND);
    }

    /**
     * 将已经获取订单锁的order进行过期
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expireLockedOrder(Order order, LocalDateTime now) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || (order.getExpiresAt() != null && order.getExpiresAt().isAfter(now))) {
            return false;
        }

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
        return true;
    }
}
