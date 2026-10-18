package me.wly.movie_reservation.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.wly.movie_reservation.order.model.OrderStatus;
import me.wly.movie_reservation.payment.gateway.*;
import me.wly.movie_reservation.payment.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.time.LocalDateTime;

/** At-least-once execution, stable provider idempotency keys, short local transactions. */
@Slf4j @Service @RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PaymentCompensationWorker {
    private final PaymentTaskService tasks;
    private final PaymentLifecycleService lifecycle;
    private final PaymentRefundService refunds;
    private final PaymentGatewayRegistry gateways;
    private final PaymentCallbackService callbacks;
    private final Clock businessClock;

    public void runFor(String paymentNo, PaymentTaskType type) {
        Long id = tasks.taskId(paymentNo, type);
        if (id != null) runOne(id);
    }

    public void runOne(Long id) {
        PaymentTaskService.Claim claim = tasks.claim(id);
        if (claim == null) return;
        try {
            boolean terminal = claim.type() == PaymentTaskType.SYNC
                    ? sync(claim.paymentNo()) : refund(claim.paymentNo());
            tasks.complete(claim, terminal);
        } catch (PaymentReconciliationException exception) {
            tasks.fail(claim, exception.getMessage(), true);
        } catch (RuntimeException exception) {
            // Provider exception messages can contain credential-bearing URLs; persist only the type.
            log.warn("Payment task {} failed: {}", id, exception.getClass().getSimpleName());
            tasks.fail(claim, "Operation failed: " + exception.getClass().getSimpleName(), false);
        }
    }

    private boolean sync(String paymentNo) {
        var local = lifecycle.snapshot(paymentNo);
        if (local.status() == PaymentStatus.SUCCEEDED || local.status() == PaymentStatus.REFUNDED
                || local.status() == PaymentStatus.REFUNDING) return true;
        PaymentGateway gateway = gateways.getGateway(local.channel());
        PaymentQueryResult remote = gateway.queryPayment(paymentNo);
        boolean payable = local.orderStatus() == OrderStatus.PENDING_PAYMENT
                && local.command().expiresAt().isAfter(LocalDateTime.now(businessClock))
                && (local.status() == PaymentStatus.CREATED || local.status() == PaymentStatus.PAYING);
        if (remote.status() == GatewayPaymentStatus.NOT_FOUND) {
            if (!paymentNo.equals(remote.paymentNo())) throw new PaymentReconciliationException("Provider payment number mismatch");
            if (!payable) {
                lifecycle.recordClosed(paymentNo);
                return true;
            }
            recordCreated(paymentNo, gateway.createPayment(local.command()));
            return false;
        }
        validate(local, remote);
        if (remote.status() == GatewayPaymentStatus.PENDING) {
            if (!payable) {
                remote = gateway.closePayment(paymentNo);
                if (remote.status() == GatewayPaymentStatus.NOT_FOUND) {
                    lifecycle.recordClosed(paymentNo);
                    return true;
                }
                validate(local, remote);
            } else {
                if (local.status() == PaymentStatus.CREATED) {
                    recordCreated(paymentNo, gateway.createPayment(local.command()));
                }
                return false;
            }
        }
        return switch (remote.status()) {
            case SUCCEEDED, FAILED -> {
                if (remote.status() == GatewayPaymentStatus.SUCCEEDED && remote.paidAt() == null) {
                    throw new PaymentReconciliationException("Successful provider payment has no paidAt");
                }
                callbacks.handleCallback(new VerifiedPaymentCallback(
                        "query:" + paymentNo + ":" + remote.status(), local.channel(), paymentNo,
                        remote.providerTradeNo(), remote.amount(), remote.status() == GatewayPaymentStatus.SUCCEEDED
                        ? PaymentCallbackStatus.SUCCESS : PaymentCallbackStatus.FAILED,
                        remote.paidAt(), remote.failureCode(), remote.failureMessage(), "{\"source\":\"provider-query\"}"));
                yield true;
            }
            case CLOSED -> { lifecycle.recordClosed(paymentNo); yield true; }
            case REFUNDED -> throw new PaymentReconciliationException("Unexpected external refund without a local refund intent");
            case PENDING -> false;
            case NOT_FOUND -> throw new PaymentReconciliationException("Unexpected provider response");
        };
    }

    private void recordCreated(String paymentNo, PaymentCreateResult result) {
        if (result == null || result.providerTradeNo() == null || result.providerTradeNo().isBlank()
                || result.payUrl() == null || result.payUrl().isBlank()) {
            throw new PaymentReconciliationException("Provider creation response is incomplete");
        }
        lifecycle.recordCreated(paymentNo, result);
    }

    private void validate(PaymentLifecycleService.Snapshot local, PaymentQueryResult remote) {
        if (!local.paymentNo().equals(remote.paymentNo()) || remote.amount() == null
                || local.command().amount().compareTo(remote.amount()) != 0
                || remote.providerTradeNo() == null || remote.providerTradeNo().isBlank()
                || (local.providerTradeNo() != null && !local.providerTradeNo().equals(remote.providerTradeNo()))) {
            throw new PaymentReconciliationException("Provider payment identifiers or amount mismatch");
        }
    }

    private boolean refund(String paymentNo) {
        var work = refunds.work(paymentNo);
        if (work == null) return true;
        PaymentGateway gateway = gateways.getGateway(work.channel());
        RefundQueryResult remote = gateway.queryRefund(work.command().refundNo());
        if (remote.status() == GatewayRefundStatus.NOT_FOUND) remote = gateway.refund(work.command());
        boolean terminal = refunds.apply(paymentNo, remote);
        if (remote.status() == GatewayRefundStatus.FAILED) {
            throw new PaymentReconciliationException("Provider rejected refund; inspect before retrying");
        }
        return terminal;
    }
}
