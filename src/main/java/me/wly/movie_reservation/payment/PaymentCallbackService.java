package me.wly.movie_reservation.payment;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.order.OrderRepository;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderStatus;
import me.wly.movie_reservation.payment.gateway.VerifiedPaymentCallback;
import me.wly.movie_reservation.payment.model.PaymentCallbackEvent;
import me.wly.movie_reservation.payment.model.PaymentCallbackProcessStatus;
import me.wly.movie_reservation.payment.model.PaymentCallbackStatus;
import me.wly.movie_reservation.payment.model.PaymentStatus;
import me.wly.movie_reservation.payment.model.PaymentTransaction;
import me.wly.movie_reservation.showtime.ShowtimeSeatRepository;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentCallbackService {
    private final PaymentCallbackEventRepository callbackEventRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;

    /**
     * The order row is always locked first. The timeout job follows the same rule,
     * so callback and timeout processing for one order are serialized.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void handleCallback(VerifiedPaymentCallback callback) {
        Long orderId = paymentTransactionRepository.findOrderIdByPaymentNo(callback.paymentNo())
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "Payment transaction not found"));

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ResultCode.ORDER_NOT_FOUND, "Order not found"));

        PaymentCallbackEvent existingEvent = callbackEventRepository
                .findByChannelAndEventId(callback.channel(), callback.eventId())
                .orElse(null);
        if (existingEvent != null) {
            acknowledgeDuplicate(existingEvent, callback);
            return;
        }

        PaymentTransaction payment = paymentTransactionRepository
                .findByPaymentNoForUpdate(callback.paymentNo())
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "Payment transaction not found"));

        PaymentCallbackEvent event = callbackEventRepository.saveAndFlush(toEvent(callback));
        validateCallback(payment, callback, event);

        if (callback.status() == PaymentCallbackStatus.FAILED) {
            processFailedCallback(order, payment, callback, event);
            return;
        }

        processSuccessfulCallback(order, payment, callback, event);
    }

    private void processFailedCallback(
            Order order,
            PaymentTransaction payment,
            VerifiedPaymentCallback callback,
            PaymentCallbackEvent event
    ) {
        payment.setCallbackPayload(callback.rawPayload());

        if (payment.getStatus() == PaymentStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.REFUNDING
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            finishEvent(event, PaymentCallbackProcessStatus.PROCESSED,
                    "Payment already reached a later state");
            return;
        }

        if (payment.getStatus() == PaymentStatus.CREATED || payment.getStatus() == PaymentStatus.PAYING) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureCode(callback.failureCode());
            payment.setFailureMessage(callback.failureMessage());
        }

        finishEvent(event, PaymentCallbackProcessStatus.PROCESSED,
                "Payment failure recorded; order remains " + order.getStatus());
    }

    private void processSuccessfulCallback(
            Order order,
            PaymentTransaction payment,
            VerifiedPaymentCallback callback,
            PaymentCallbackEvent event
    ) {
        LocalDateTime paidAt = toLocalDateTime(callback.paidAt());
        payment.setCallbackPayload(callback.rawPayload());

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            finishEvent(event, PaymentCallbackProcessStatus.PROCESSED,
                    "Duplicate payment success acknowledged");
            return;
        }
        if (payment.getStatus() == PaymentStatus.REFUNDING
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            finishEvent(event, PaymentCallbackProcessStatus.REFUND_REQUIRED,
                    "Payment has already entered the refund flow");
            return;
        }

        if (!canFulfilOrder(order, payment, paidAt)) {
            markRefundRequired(order, payment, callback, paidAt, event,
                    "Order or payment was no longer payable when payment succeeded");
            return;
        }

        List<ShowtimeSeat> lockedSeats = showtimeSeatRepository.findLockedByOrderIdForUpdate(order.getId());
        if (lockedSeats.isEmpty() || lockedSeats.size() != order.getOrderSeats().size()) {
            markRefundRequired(order, payment, callback, paidAt, event,
                    "The order no longer owns all of its locked seats");
            return;
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProviderTradeNo(callback.providerTradeNo());
        payment.setPaidAt(paidAt);
        payment.setFailureCode(null);
        payment.setFailureMessage(null);

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(paidAt);
        order.setUpdatedAt(LocalDateTime.now());

        for (ShowtimeSeat seat : lockedSeats) {
            seat.setStatus(SeatStatus.SOLD);
            seat.setLockToken(null);
            seat.setLockUntil(null);
        }

        finishEvent(event, PaymentCallbackProcessStatus.PROCESSED,
                "Payment succeeded and seats were sold");
    }

    private boolean canFulfilOrder(Order order, PaymentTransaction payment, LocalDateTime paidAt) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || (payment.getStatus() != PaymentStatus.CREATED && payment.getStatus() != PaymentStatus.PAYING)) {
            return false;
        }

        LocalDateTime deadline = earlier(order.getExpiresAt(), payment.getExpiresAt());
        return deadline != null && !paidAt.isAfter(deadline); // 订单、Payment状态不作为支付成功依据而是精确的过期时间
    }

    private void markRefundRequired(
            Order order,
            PaymentTransaction payment,
            VerifiedPaymentCallback callback,
            LocalDateTime paidAt,
            PaymentCallbackEvent event,
            String reason
    ) {
        payment.setStatus(PaymentStatus.REFUNDING);
        payment.setProviderTradeNo(callback.providerTradeNo());
        payment.setPaidAt(paidAt);

        if (order.getStatus() != OrderStatus.PAID) {
            order.setStatus(OrderStatus.REFUNDING);
            order.setUpdatedAt(LocalDateTime.now());

            List<ShowtimeSeat> lockedSeats = showtimeSeatRepository.findLockedByOrderIdForUpdate(order.getId());
            for (ShowtimeSeat seat : lockedSeats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setOrder(null);
                seat.setLockToken(null);
                seat.setLockUntil(null);
            }
        }

        finishEvent(event, PaymentCallbackProcessStatus.REFUND_REQUIRED, reason);
    }

    private void validateCallback(
            PaymentTransaction payment,
            VerifiedPaymentCallback callback,
            PaymentCallbackEvent event
    ) {
        if (payment.getAmount().compareTo(callback.amount()) != 0) {
            reject(event, "Payment amount mismatch");
        }
        if (payment.getProviderTradeNo() != null
                && !payment.getProviderTradeNo().equals(callback.providerTradeNo())) {
            reject(event, "Provider trade number mismatch");
        }
        if (payment.getProviderTradeNo() == null) {
            payment.setProviderTradeNo(callback.providerTradeNo());
        }
    }

    // 确认是重复的回调事件，eventId复用或者已被拒绝的回调
    private void acknowledgeDuplicate(
            PaymentCallbackEvent event,
            VerifiedPaymentCallback callback
    ) {
        event.setDuplicateCount(event.getDuplicateCount() + 1);
        event.setLastReceivedAt(LocalDateTime.now());

        if (!sameEvent(event, callback)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "Callback eventId was reused with different payment data");
        }
        if (event.getProcessStatus() == PaymentCallbackProcessStatus.REJECTED) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    event.getProcessingMessage() == null ? "Payment callback was rejected" : event.getProcessingMessage());
        }
    }

    private boolean sameEvent(PaymentCallbackEvent event, VerifiedPaymentCallback callback) {
        return event.getPaymentNo().equals(callback.paymentNo())
                && event.getProviderTradeNo().equals(callback.providerTradeNo())
                && event.getAmount().compareTo(callback.amount()) == 0
                && event.getCallbackStatus() == callback.status();
    }

    private PaymentCallbackEvent toEvent(VerifiedPaymentCallback callback) {
        PaymentCallbackEvent event = new PaymentCallbackEvent();
        event.setChannel(callback.channel());
        event.setEventId(callback.eventId());
        event.setPaymentNo(callback.paymentNo());
        event.setProviderTradeNo(callback.providerTradeNo());
        event.setAmount(callback.amount());
        event.setCallbackStatus(callback.status());
        event.setProcessStatus(PaymentCallbackProcessStatus.RECEIVED);
        event.setPaidAt(toLocalDateTime(callback.paidAt()));
        event.setFailureCode(callback.failureCode());
        event.setFailureMessage(callback.failureMessage());
        event.setRawPayload(callback.rawPayload());
        return event;
    }

    private void finishEvent(
            PaymentCallbackEvent event,
            PaymentCallbackProcessStatus processStatus,
            String message
    ) {
        event.setProcessStatus(processStatus);
        event.setProcessingMessage(message);
        event.setProcessedAt(LocalDateTime.now());
    }

    private void reject(PaymentCallbackEvent event, String message) {
        finishEvent(event, PaymentCallbackProcessStatus.REJECTED, message);
        throw new BusinessException(ResultCode.BAD_REQUEST, message);
    }

    private LocalDateTime earlier(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isBefore(second) ? first : second;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
