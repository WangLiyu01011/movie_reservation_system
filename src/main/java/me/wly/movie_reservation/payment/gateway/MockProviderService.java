package me.wly.movie_reservation.payment.gateway;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.*;
import me.wly.movie_reservation.common.util.UuidCodeGenerator;
import me.wly.movie_reservation.payment.model.PaymentCallbackStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Durable simulated third party. These commits are independent of merchant transactions. */
@Service @RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class MockProviderService {
    private final JdbcTemplate jdbc;
    private final Clock businessClock;

    public PaymentCreateResult create(PaymentCreateCommand command, String payUrl) {
        jdbc.update("""
                INSERT INTO mock_payment_order(payment_no,provider_trade_no,amount,status,expires_at,pay_url)
                VALUES (?,?,?,'PENDING',?,?) ON DUPLICATE KEY UPDATE payment_no=payment_no
                """, command.paymentNo(), UuidCodeGenerator.generateCode("mock_trade_"), command.amount(),
                command.expiresAt(), payUrl);
        ProviderOrder order = load(command.paymentNo(), true);
        if (order.amount().compareTo(command.amount()) != 0) throw badRequest("Merchant payment number reused with another amount");
        return new PaymentCreateResult(order.tradeNo(), order.payUrl(), order.expiresAt());
    }

    public PaymentQueryResult query(String paymentNo) {
        ProviderOrder order = load(paymentNo, true);
        if (order == null) return new PaymentQueryResult(paymentNo, null, null, GatewayPaymentStatus.NOT_FOUND, null, null, null);
        if (order.status() == GatewayPaymentStatus.PENDING && !order.expiresAt().isAfter(now())) {
            jdbc.update("UPDATE mock_payment_order SET status='CLOSED' WHERE payment_no=?", paymentNo);
            order = load(paymentNo, false);
        }
        return toQuery(order);
    }

    public PaymentQueryResult close(String paymentNo) {
        ProviderOrder order = load(paymentNo, true);
        if (order == null) return new PaymentQueryResult(paymentNo, null, null, GatewayPaymentStatus.NOT_FOUND, null, null, null);
        if (order.status() == GatewayPaymentStatus.PENDING) {
            jdbc.update("UPDATE mock_payment_order SET status='CLOSED' WHERE payment_no=?", paymentNo);
        }
        return toQuery(load(paymentNo, false));
    }

    public RefundQueryResult refund(RefundCommand command) {
        ProviderOrder order = load(command.paymentNo(), true);
        if (order == null || !order.tradeNo().equals(command.providerTradeNo())
                || order.amount().compareTo(command.amount()) != 0) throw badRequest("Mock refund payment or amount mismatch");
        RefundQueryResult existing = refundByNo(command.refundNo());
        if (existing.status() != GatewayRefundStatus.NOT_FOUND) {
            if (!existing.paymentNo().equals(command.paymentNo()) || existing.amount().compareTo(command.amount()) != 0) {
                throw badRequest("Mock refund idempotency conflict");
            }
            return existing;
        }
        if (order.status() != GatewayPaymentStatus.SUCCEEDED) throw badRequest("Mock payment is not refundable");
        jdbc.update("""
                INSERT INTO mock_payment_refund(refund_no,payment_no,provider_refund_no,amount,refunded_at)
                VALUES (?,?,?,?,?)
                """, command.refundNo(), command.paymentNo(), UuidCodeGenerator.generateCode("mock_refund_"), command.amount(), now());
        jdbc.update("UPDATE mock_payment_order SET status='REFUNDED' WHERE payment_no=?", command.paymentNo());
        return refundByNo(command.refundNo());
    }

    public RefundQueryResult queryRefund(String refundNo) { return refundByNo(refundNo); }

    /** Used by the authenticated mock checkout; payment time is always assigned server-side. */
    public VerifiedPaymentCallback complete(String paymentNo, PaymentCallbackStatus requested) {
        ProviderOrder order = load(paymentNo, true);
        if (order == null) throw badRequest("Mock payment not found");
        if (order.status() == GatewayPaymentStatus.CLOSED || order.status() == GatewayPaymentStatus.REFUNDED) {
            throw badRequest("Mock payment is no longer payable");
        }
        if (order.status() == GatewayPaymentStatus.PENDING) {
            jdbc.update("UPDATE mock_payment_order SET status=?,paid_at=?,failure_code=?,failure_message=? WHERE payment_no=?",
                    requested == PaymentCallbackStatus.SUCCESS ? "SUCCEEDED" : "FAILED",
                    requested == PaymentCallbackStatus.SUCCESS ? now() : null,
                    requested == PaymentCallbackStatus.SUCCESS ? null : "MOCK_DECLINED",
                    requested == PaymentCallbackStatus.SUCCESS ? null : "Mock payment declined", paymentNo);
        }
        order = load(paymentNo, false);
        PaymentCallbackStatus actual = order.status() == GatewayPaymentStatus.SUCCEEDED
                ? PaymentCallbackStatus.SUCCESS : PaymentCallbackStatus.FAILED;
        return new VerifiedPaymentCallback("mock:" + paymentNo + ":" + actual, "MOCK", paymentNo, order.tradeNo(),
                order.amount(), actual, toInstant(order.paidAt()), order.failureCode(), order.failureMessage(),
                "{\"source\":\"mock-checkout\"}");
    }

    /** A valid signed callback is also a simulated provider notification, independently durable. */
    public void recordCallback(VerifiedPaymentCallback callback) {
        ProviderOrder order = load(callback.paymentNo(), true);
        if (order == null || !order.tradeNo().equals(callback.providerTradeNo())
                || order.amount().compareTo(callback.amount()) != 0) throw badRequest("Mock callback payment mismatch");
        if (order.status() == GatewayPaymentStatus.REFUNDED || order.status() == GatewayPaymentStatus.SUCCEEDED) return;
        boolean succeeded = callback.status() == PaymentCallbackStatus.SUCCESS;
        jdbc.update("UPDATE mock_payment_order SET status=?,paid_at=?,failure_code=?,failure_message=? WHERE payment_no=?",
                succeeded ? "SUCCEEDED" : "FAILED", callback.paidAt() == null ? null
                        : LocalDateTime.ofInstant(callback.paidAt(), businessClock.getZone()),
                callback.failureCode(), callback.failureMessage(), callback.paymentNo());
    }

    private ProviderOrder load(String paymentNo, boolean lock) {
        List<ProviderOrder> rows = jdbc.query("SELECT * FROM mock_payment_order WHERE payment_no=?" + (lock ? " FOR UPDATE" : ""),
                (row, index) -> new ProviderOrder(row.getString("payment_no"), row.getString("provider_trade_no"),
                        row.getBigDecimal("amount"), GatewayPaymentStatus.valueOf(row.getString("status")),
                        row.getObject("expires_at", LocalDateTime.class), row.getString("pay_url"),
                        row.getObject("paid_at", LocalDateTime.class), row.getString("failure_code"), row.getString("failure_message")), paymentNo);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private RefundQueryResult refundByNo(String refundNo) {
        var rows = jdbc.query("SELECT * FROM mock_payment_refund WHERE refund_no=?", (row, index) ->
                new RefundQueryResult(refundNo, row.getString("payment_no"), row.getString("provider_refund_no"),
                        row.getBigDecimal("amount"), GatewayRefundStatus.SUCCEEDED,
                        toInstant(row.getObject("refunded_at", LocalDateTime.class)), null), refundNo);
        return rows.isEmpty() ? new RefundQueryResult(refundNo, null, null, null, GatewayRefundStatus.NOT_FOUND, null, null) : rows.getFirst();
    }
    private PaymentQueryResult toQuery(ProviderOrder order) {
        return new PaymentQueryResult(order.paymentNo(), order.tradeNo(), order.amount(), order.status(),
                toInstant(order.paidAt()), order.failureCode(), order.failureMessage());
    }
    private LocalDateTime now() { return LocalDateTime.now(businessClock).truncatedTo(ChronoUnit.MILLIS); }
    private Instant toInstant(LocalDateTime time) { return time == null ? null : time.atZone(businessClock.getZone()).toInstant(); }
    private BusinessException badRequest(String message) { return new BusinessException(ResultCode.BAD_REQUEST, message); }
    private record ProviderOrder(String paymentNo, String tradeNo, BigDecimal amount, GatewayPaymentStatus status,
                                 LocalDateTime expiresAt, String payUrl, LocalDateTime paidAt,
                                 String failureCode, String failureMessage) { }
}
