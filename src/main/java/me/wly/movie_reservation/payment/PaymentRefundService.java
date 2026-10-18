package me.wly.movie_reservation.payment;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.*;
import me.wly.movie_reservation.common.util.UuidCodeGenerator;
import me.wly.movie_reservation.order.OrderRepository;
import me.wly.movie_reservation.order.model.*;
import me.wly.movie_reservation.payment.dto.RefundRequestDTO;
import me.wly.movie_reservation.payment.gateway.*;
import me.wly.movie_reservation.payment.model.*;
import me.wly.movie_reservation.payment.vo.RefundVO;
import me.wly.movie_reservation.showtime.ShowtimeSeatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class PaymentRefundService {
    private final PaymentRefundRepository refunds;
    private final PaymentTransactionRepository payments;
    private final OrderRepository orders;
    private final PaymentTaskService tasks;
    private final ShowtimeSeatService seats;
    private final Clock businessClock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void request(String paymentNo, RefundRequestDTO dto, String username) {
        PaymentTransaction payment = lockedPayment(paymentNo);
        Order order = payment.getOrder();
        if (!order.getUser().getUsername().equals(username)) throw notFound();
        if (refunds.findForUpdate(payment.getId()).isPresent()) return;
        if (payment.getStatus() != PaymentStatus.SUCCEEDED || order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Only a paid order can be refunded");
        }
        if (order.getStartTime() == null || !order.getStartTime().isAfter(LocalDateTime.now(businessClock))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Refunds are only supported before the show starts");
        }
        payment.setStatus(PaymentStatus.REFUNDING);
        order.setStatus(OrderStatus.REFUNDING);
        create(payment, dto.requestId(), dto.reason(), true);
    }

    /** Called in the same transaction that marks a late/unfulfillable charge REFUNDING. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAutomaticRefund(PaymentTransaction payment, String reason) {
        if (refunds.findForUpdate(payment.getId()).isEmpty()) {
            create(payment, "automatic:" + payment.getPaymentNo(), reason, false);
        }
    }

    @Transactional(readOnly = true)
    public RefundVO ownedRefund(String paymentNo, String username) {
        PaymentTransaction payment = payments.findByPaymentNo(paymentNo).orElseThrow(this::notFound);
        if (!payment.getOrder().getUser().getUsername().equals(username)) throw notFound();
        return toVO(refunds.findByPayment_Id(payment.getId()).orElseThrow(this::notFound));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundWork work(String paymentNo) {
        PaymentTransaction payment = lockedPayment(paymentNo);
        PaymentRefund refund = refunds.findForUpdate(payment.getId()).orElseThrow(this::notFound);
        if (refund.getStatus() == RefundStatus.SUCCEEDED) return null;
        if (payment.getStatus() != PaymentStatus.REFUNDING) {
            throw new PaymentReconciliationException("Refund task does not match payment state");
        }
        refund.setStatus(RefundStatus.PROCESSING);
        return new RefundWork(payment.getChannel(), new RefundCommand(refund.getRefundNo(), paymentNo,
                payment.getProviderTradeNo(), refund.getAmount(), refund.getReason()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean apply(String paymentNo, RefundQueryResult result) {
        PaymentTransaction payment = lockedPayment(paymentNo);
        PaymentRefund refund = refunds.findForUpdate(payment.getId()).orElseThrow(this::notFound);
        if (refund.getStatus() == RefundStatus.SUCCEEDED) return true;
        if (!refund.getRefundNo().equals(result.refundNo()) || !paymentNo.equals(result.paymentNo())
                || result.amount() == null || refund.getAmount().compareTo(result.amount()) != 0) {
            throw new PaymentReconciliationException("Provider refund identifiers or amount mismatch");
        }
        if (result.status() == GatewayRefundStatus.SUCCEEDED) {
            if (result.refundedAt() == null || result.providerRefundNo() == null) {
                throw new PaymentReconciliationException("Provider refund confirmation is incomplete");
            }
            if (refund.isReleaseSoldSeats()) seats.releaseSoldForOrder(payment.getOrder());
            refund.setStatus(RefundStatus.SUCCEEDED);
            refund.setProviderRefundNo(result.providerRefundNo());
            refund.setRefundedAt(LocalDateTime.ofInstant(result.refundedAt(), businessClock.getZone()));
            refund.setFailureMessage(null);
            payment.setStatus(PaymentStatus.REFUNDED);
            Order order = payment.getOrder();
            if (order.getStatus() == OrderStatus.REFUNDING && payments.findAllByOrder_IdAndStatusIn(order.getId(),
                    List.of(PaymentStatus.SUCCEEDED, PaymentStatus.REFUNDING)).isEmpty()) {
                order.setStatus(OrderStatus.REFUNDED);
            }
            return true;
        }
        if (result.status() == GatewayRefundStatus.FAILED) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureMessage("Provider rejected the refund; manual inspection is required");
        }
        return false;
    }

    private void create(PaymentTransaction payment, String requestId, String reason, boolean releaseSold) {
        PaymentRefund refund = new PaymentRefund();
        refund.setRefundNo(UuidCodeGenerator.generateCode("ref_"));
        refund.setPayment(payment);
        refund.setRequestId(requestId);
        refund.setAmount(payment.getAmount());
        refund.setReason(reason);
        refund.setStatus(RefundStatus.PENDING);
        refund.setReleaseSoldSeats(releaseSold);
        refund.setCreatedAt(LocalDateTime.now(businessClock));
        refunds.saveAndFlush(refund);
        tasks.enqueue(payment, PaymentTaskType.REFUND);
    }
    private PaymentTransaction lockedPayment(String paymentNo) {
        Long orderId = payments.findOrderIdByPaymentNo(paymentNo).orElseThrow(this::notFound);
        orders.findByIdForUpdate(orderId).orElseThrow(this::notFound);
        return payments.findByPaymentNoForUpdate(paymentNo).orElseThrow(this::notFound);
    }
    private RefundVO toVO(PaymentRefund refund) {
        return new RefundVO(refund.getRefundNo(), refund.getPayment().getPaymentNo(), refund.getAmount(),
                refund.getStatus(), refund.getFailureMessage(), refund.getRefundedAt());
    }
    private BusinessException notFound() { return new BusinessException(ResultCode.ORDER_NOT_FOUND, "Payment or refund not found"); }
    public record RefundWork(String channel, RefundCommand command) { }
}
