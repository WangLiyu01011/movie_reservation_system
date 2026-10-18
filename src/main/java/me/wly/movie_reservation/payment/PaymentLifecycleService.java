package me.wly.movie_reservation.payment;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.*;
import me.wly.movie_reservation.order.*;
import me.wly.movie_reservation.order.model.*;
import me.wly.movie_reservation.payment.dto.PaymentRequestDTO;
import me.wly.movie_reservation.payment.gateway.*;
import me.wly.movie_reservation.payment.model.*;
import me.wly.movie_reservation.payment.vo.PaymentRequestVO;
import me.wly.movie_reservation.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Locale;

/** Local transactions only; provider I/O belongs to the worker. */
@Service @RequiredArgsConstructor
public class PaymentLifecycleService {
    private final Clock businessClock;
    private final PaymentTransactionRepository payments;
    private final OrderRepository orders;
    private final UserRepository users;
    private final OrderExpirationService expiration;
    private final PaymentTaskService tasks;
    private final EntityManager entityManager;

    @Transactional(noRollbackFor = BusinessException.class, propagation = Propagation.REQUIRES_NEW)
    public String prepare(PaymentRequestDTO dto, String username, String channel) {
        var user = users.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "User not found"));
        Order order = orders.findByCodeAndUserIdForUpdate(dto.orderCode(), user.getId())
                .orElseThrow(() -> new BusinessException(ResultCode.ORDER_NOT_FOUND, "Order not found"));
        boolean expired = expiration.expireLockedOrder(order, LocalDateTime.now(businessClock));
        PaymentTransaction sameRequest = payments.findByOrder_IdAndRequestId(order.getId(), dto.requestId()).orElse(null);
        if (sameRequest != null) {
            requireSameChannel(sameRequest, channel);
            return sameRequest.getPaymentNo(); // Even a terminal result is replayed after a lost HTTP response.
        }
        if (expired) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Order has expired");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Order cannot be paid in status: " + order.getStatus());
        }
        if (order.getTotalPrice() == null || order.getTotalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Order price must be positive");
        }
        PaymentTransaction existing = payments.findFirstByOrder_IdAndStatusInOrderByIdDesc(order.getId(),
                List.of(PaymentStatus.CREATED, PaymentStatus.PAYING)).orElse(null);
        if (existing != null) {
            requireSameChannel(existing, channel);
            return existing.getPaymentNo();
        }
        PaymentTransaction payment = new PaymentTransaction();
        payment.setOrder(order);
        payment.setRequestId(dto.requestId());
        payment.setChannel(channel);
        payment.setAmount(order.getTotalPrice());
        payment.setStatus(PaymentStatus.CREATED);
        PaymentTransaction saved = payments.saveAndFlush(payment);
        entityManager.refresh(saved);
        tasks.enqueue(saved, PaymentTaskType.SYNC);
        return saved.getPaymentNo();
    }

    @Transactional(readOnly = true)
    public PaymentRequestVO ownedPayment(String paymentNo, String username) {
        PaymentTransaction payment = payments.findByPaymentNo(paymentNo).orElseThrow(this::notFound);
        if (!payment.getOrder().getUser().getUsername().equals(username)) throw notFound();
        return new PaymentRequestVO(payment.getPaymentNo(), payment.getOrder().getCode(), payment.getChannel(),
                payment.getAmount(), payment.getStatus(), deadline(payment), payment.getPayUrl());
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(String paymentNo) {
        PaymentTransaction payment = payments.findByPaymentNo(paymentNo).orElseThrow(this::notFound);
        Order order = payment.getOrder();
        return new Snapshot(paymentNo, payment.getChannel(), payment.getStatus(), payment.getProviderTradeNo(),
                order.getStatus(), new PaymentCreateCommand(paymentNo, order.getCode(), payment.getAmount(),
                order.getMovieTitle() + " " + order.getOrderSeats().size(), deadline(payment),
                "/api/v1/payment-callbacks/" + payment.getChannel().toLowerCase(Locale.ROOT)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCreated(String paymentNo, PaymentCreateResult result) {
        PaymentTransaction payment = lockedPayment(paymentNo);
        if (payment.getProviderTradeNo() != null && !payment.getProviderTradeNo().equals(result.providerTradeNo())) {
            throw new PaymentReconciliationException("Provider changed its trade number for the same payment");
        }
        payment.setProviderTradeNo(result.providerTradeNo());
        payment.setPayUrl(result.payUrl());
        if (payment.getStatus() == PaymentStatus.CREATED) payment.setStatus(PaymentStatus.PAYING);
        if (payment.getStatus() == PaymentStatus.CLOSED || payment.getStatus() == PaymentStatus.FAILED
                || (payment.getStatus() == PaymentStatus.PAYING && (payment.getOrder().getStatus() != OrderStatus.PENDING_PAYMENT
                || !deadline(payment).isAfter(LocalDateTime.now(businessClock))))) {
            tasks.wakeSyncAfterLateCreate(payment);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordClosed(String paymentNo) {
        PaymentTransaction payment = lockedPayment(paymentNo);
        if (payment.getStatus() == PaymentStatus.CREATED || payment.getStatus() == PaymentStatus.PAYING) {
            payment.setStatus(PaymentStatus.CLOSED);
        }
    }

    private PaymentTransaction lockedPayment(String paymentNo) {
        Long orderId = payments.findOrderIdByPaymentNo(paymentNo).orElseThrow(this::notFound);
        orders.findByIdForUpdate(orderId).orElseThrow(this::notFound);
        return payments.findByPaymentNoForUpdate(paymentNo).orElseThrow(this::notFound);
    }
    private void requireSameChannel(PaymentTransaction payment, String channel) {
        if (!channel.equals(payment.getChannel())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "An existing payment uses a different channel");
        }
    }
    private LocalDateTime deadline(PaymentTransaction payment) {
        LocalDateTime first = payment.getOrder().getExpiresAt();
        LocalDateTime second = payment.getExpiresAt();
        if (first == null) return second;
        if (second == null) return first;
        return first.isBefore(second) ? first : second;
    }
    private BusinessException notFound() { return new BusinessException(ResultCode.ORDER_NOT_FOUND, "Payment not found"); }
    public record Snapshot(String paymentNo, String channel, PaymentStatus status, String providerTradeNo,
                           OrderStatus orderStatus, PaymentCreateCommand command) { }
}
