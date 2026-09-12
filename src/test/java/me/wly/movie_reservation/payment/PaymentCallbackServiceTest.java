package me.wly.movie_reservation.payment;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.order.OrderRepository;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderSeat;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCallbackServiceTest {
    private static final Long ORDER_ID = 100L;
    private static final String PAYMENT_NO = "pay_test";
    private static final String PROVIDER_TRADE_NO = "mock_trade_test";
    private static final String CHANNEL = "MOCK";
    private static final String EVENT_ID = "evt_test";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final Instant PAID_AT = Instant.parse("2026-09-12T02:00:00Z");
    private static final String RAW_PAYLOAD = "{\"eventId\":\"evt_test\"}";

    @Mock
    private PaymentCallbackEventRepository callbackEventRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ShowtimeSeatRepository showtimeSeatRepository;
    @InjectMocks
    private PaymentCallbackService paymentCallbackService;

    @Test
    void handleCallback_marksOrderPaidAndSeatsSoldForValidSuccess() {
        Order order = order(OrderStatus.PENDING_PAYMENT, 2, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, PaymentStatus.PAYING, paidAt().plusMinutes(3));
        ShowtimeSeat seatA = lockedSeat(order);
        ShowtimeSeat seatB = lockedSeat(order);
        stubNewEventFlow(order, payment);
        when(showtimeSeatRepository.findLockedByOrderIdForUpdate(ORDER_ID))
                .thenReturn(List.of(seatA, seatB));

        paymentCallbackService.handleCallback(successCallback());

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(PROVIDER_TRADE_NO, payment.getProviderTradeNo());
        assertEquals(paidAt(), payment.getPaidAt());
        assertEquals(RAW_PAYLOAD, payment.getCallbackPayload());
        assertNull(payment.getFailureCode());
        assertNull(payment.getFailureMessage());
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(paidAt(), order.getPaidAt());
        assertNotNull(order.getUpdatedAt());
        assertSold(seatA, order);
        assertSold(seatB, order);

        PaymentCallbackEvent event = savedEvent();
        assertEquals(CHANNEL, event.getChannel());
        assertEquals(EVENT_ID, event.getEventId());
        assertEquals(PAYMENT_NO, event.getPaymentNo());
        assertEquals(PROVIDER_TRADE_NO, event.getProviderTradeNo());
        assertEquals(AMOUNT, event.getAmount());
        assertEquals(PaymentCallbackStatus.SUCCESS, event.getCallbackStatus());
        assertEquals(paidAt(), event.getPaidAt());
        assertEquals(RAW_PAYLOAD, event.getRawPayload());
        assertEquals(PaymentCallbackProcessStatus.PROCESSED, event.getProcessStatus());
        assertEquals("Payment succeeded and seats were sold", event.getProcessingMessage());
        assertNotNull(event.getProcessedAt());
    }

    @Test
    void handleCallback_recordsProviderFailureAndLeavesOrderPending() {
        Order order = order(OrderStatus.PENDING_PAYMENT, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, PaymentStatus.PAYING, paidAt().plusMinutes(3));
        stubNewEventFlow(order, payment);

        paymentCallbackService.handleCallback(failedCallback());

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("INSUFFICIENT_BALANCE", payment.getFailureCode());
        assertEquals("Insufficient balance", payment.getFailureMessage());
        assertEquals(RAW_PAYLOAD, payment.getCallbackPayload());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        verify(showtimeSeatRepository, never()).findLockedByOrderIdForUpdate(any());

        PaymentCallbackEvent event = savedEvent();
        assertEquals(PaymentCallbackStatus.FAILED, event.getCallbackStatus());
        assertEquals(PaymentCallbackProcessStatus.PROCESSED, event.getProcessStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCEEDED", "REFUNDING", "REFUNDED"})
    void handleCallback_ignoresFailureAfterPaymentReachedLaterState(PaymentStatus status) {
        Order order = order(OrderStatus.PAID, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, status, paidAt().plusMinutes(3));
        stubNewEventFlow(order, payment);

        paymentCallbackService.handleCallback(failedCallback());

        assertEquals(status, payment.getStatus());
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(PaymentCallbackProcessStatus.PROCESSED, savedEvent().getProcessStatus());
        verify(showtimeSeatRepository, never()).findLockedByOrderIdForUpdate(any());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "CLOSED"})
    void handleCallback_doesNotRewriteOtherTerminalStatesForFailure(PaymentStatus status) {
        Order order = order(OrderStatus.PENDING_PAYMENT, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, status, paidAt().plusMinutes(3));
        stubNewEventFlow(order, payment);

        paymentCallbackService.handleCallback(failedCallback());

        assertEquals(status, payment.getStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentCallbackProcessStatus.PROCESSED, savedEvent().getProcessStatus());
        verify(showtimeSeatRepository, never()).findLockedByOrderIdForUpdate(any());
    }

    @Test
    void handleCallback_acknowledgesSameEventWithoutProcessingAgain() {
        Order order = order(OrderStatus.PAID, 1, paidAt().plusMinutes(5));
        PaymentCallbackEvent existing = existingEvent(PaymentCallbackProcessStatus.PROCESSED);
        existing.setDuplicateCount(2);
        stubExistingEventFlow(order, existing);

        paymentCallbackService.handleCallback(successCallback());

        assertEquals(3, existing.getDuplicateCount());
        assertNotNull(existing.getLastReceivedAt());
        verify(paymentTransactionRepository, never()).findByPaymentNoForUpdate(any());
        verify(callbackEventRepository, never()).saveAndFlush(any());
        verify(showtimeSeatRepository, never()).findLockedByOrderIdForUpdate(any());
    }

    @Test
    void handleCallback_rejectsReusedEventIdWithDifferentPayload() {
        Order order = order(OrderStatus.PAID, 1, paidAt().plusMinutes(5));
        PaymentCallbackEvent existing = existingEvent(PaymentCallbackProcessStatus.PROCESSED);
        stubExistingEventFlow(order, existing);
        VerifiedPaymentCallback changedAmount = callback(
                PaymentCallbackStatus.SUCCESS,
                new BigDecimal("99.00"),
                PROVIDER_TRADE_NO,
                PAID_AT,
                null,
                null
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> paymentCallbackService.handleCallback(changedAmount)
        );

        assertEquals("Callback eventId was reused with different payment data", exception.getMessage());
        assertEquals(1, existing.getDuplicateCount());
        verify(paymentTransactionRepository, never()).findByPaymentNoForUpdate(any());
    }

    @Test
    void handleCallback_rejectsDuplicateOfPreviouslyRejectedEvent() {
        Order order = order(OrderStatus.PENDING_PAYMENT, 1, paidAt().plusMinutes(5));
        PaymentCallbackEvent existing = existingEvent(PaymentCallbackProcessStatus.REJECTED);
        existing.setProcessingMessage("Payment amount mismatch");
        stubExistingEventFlow(order, existing);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> paymentCallbackService.handleCallback(successCallback())
        );

        assertEquals("Payment amount mismatch", exception.getMessage());
        assertEquals(1, existing.getDuplicateCount());
    }

    @Test
    void handleCallback_rejectsAmountMismatchAndRecordsRejectedEvent() {
        Order order = order(OrderStatus.PENDING_PAYMENT, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, PaymentStatus.PAYING, paidAt().plusMinutes(3));
        stubNewEventFlow(order, payment);
        VerifiedPaymentCallback changedAmount = callback(
                PaymentCallbackStatus.SUCCESS,
                new BigDecimal("101.00"),
                PROVIDER_TRADE_NO,
                PAID_AT,
                null,
                null
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> paymentCallbackService.handleCallback(changedAmount)
        );

        assertEquals("Payment amount mismatch", exception.getMessage());
        assertEquals(PaymentStatus.PAYING, payment.getStatus());
        PaymentCallbackEvent event = savedEvent();
        assertEquals(PaymentCallbackProcessStatus.REJECTED, event.getProcessStatus());
        assertEquals("Payment amount mismatch", event.getProcessingMessage());
        assertNotNull(event.getProcessedAt());
    }

    @Test
    void handleCallback_rejectsProviderTradeNumberMismatch() {
        Order order = order(OrderStatus.PENDING_PAYMENT, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, PaymentStatus.PAYING, paidAt().plusMinutes(3));
        stubNewEventFlow(order, payment);
        VerifiedPaymentCallback changedTradeNo = callback(
                PaymentCallbackStatus.SUCCESS,
                AMOUNT,
                "mock_trade_other",
                PAID_AT,
                null,
                null
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> paymentCallbackService.handleCallback(changedTradeNo)
        );

        assertEquals("Provider trade number mismatch", exception.getMessage());
        assertEquals(PaymentCallbackProcessStatus.REJECTED, savedEvent().getProcessStatus());
    }

    @Test
    void handleCallback_acceptsRepeatedSuccessForSucceededPayment() {
        Order order = order(OrderStatus.PAID, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, PaymentStatus.SUCCEEDED, paidAt().plusMinutes(3));
        stubNewEventFlow(order, payment);

        paymentCallbackService.handleCallback(successCallback());

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(OrderStatus.PAID, order.getStatus());
        PaymentCallbackEvent event = savedEvent();
        assertEquals(PaymentCallbackProcessStatus.PROCESSED, event.getProcessStatus());
        assertEquals("Duplicate payment success acknowledged", event.getProcessingMessage());
        verify(showtimeSeatRepository, never()).findLockedByOrderIdForUpdate(any());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"REFUNDING", "REFUNDED"})
    void handleCallback_acknowledgesSuccessWhenPaymentAlreadyInRefundFlow(PaymentStatus status) {
        Order order = order(OrderStatus.REFUNDING, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, status, paidAt().plusMinutes(3));
        stubNewEventFlow(order, payment);

        paymentCallbackService.handleCallback(successCallback());

        assertEquals(status, payment.getStatus());
        assertEquals(PaymentCallbackProcessStatus.REFUND_REQUIRED, savedEvent().getProcessStatus());
        verify(showtimeSeatRepository, never()).findLockedByOrderIdForUpdate(any());
    }

    @Test
    void handleCallback_marksLatePaymentForRefundAndReleasesSeats() {
        Order order = order(OrderStatus.PENDING_PAYMENT, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, PaymentStatus.PAYING, paidAt().minusSeconds(1));
        ShowtimeSeat seat = lockedSeat(order);
        stubNewEventFlow(order, payment);
        when(showtimeSeatRepository.findLockedByOrderIdForUpdate(ORDER_ID)).thenReturn(List.of(seat));

        paymentCallbackService.handleCallback(successCallback());

        assertEquals(PaymentStatus.REFUNDING, payment.getStatus());
        assertEquals(OrderStatus.REFUNDING, order.getStatus());
        assertEquals(paidAt(), payment.getPaidAt());
        assertAvailable(seat);
        assertEquals(PaymentCallbackProcessStatus.REFUND_REQUIRED, savedEvent().getProcessStatus());
    }

    @Test
    void handleCallback_refundsSuccessReceivedAfterPaymentWasMarkedFailed() {
        Order order = order(OrderStatus.PENDING_PAYMENT, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, PaymentStatus.FAILED, paidAt().plusMinutes(3));
        ShowtimeSeat seat = lockedSeat(order);
        stubNewEventFlow(order, payment);
        when(showtimeSeatRepository.findLockedByOrderIdForUpdate(ORDER_ID)).thenReturn(List.of(seat));

        paymentCallbackService.handleCallback(successCallback());

        assertEquals(PaymentStatus.REFUNDING, payment.getStatus());
        assertEquals(OrderStatus.REFUNDING, order.getStatus());
        assertAvailable(seat);
        assertEquals(PaymentCallbackProcessStatus.REFUND_REQUIRED, savedEvent().getProcessStatus());
    }

    @Test
    void handleCallback_refundsSecondPaymentButKeepsAlreadyPaidOrderAndSeats() {
        Order order = order(OrderStatus.PAID, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, PaymentStatus.PAYING, paidAt().plusMinutes(3));
        stubNewEventFlow(order, payment);

        paymentCallbackService.handleCallback(successCallback());

        assertEquals(PaymentStatus.REFUNDING, payment.getStatus());
        assertEquals(OrderStatus.PAID, order.getStatus());
        verify(showtimeSeatRepository, never()).findLockedByOrderIdForUpdate(any());
        assertEquals(PaymentCallbackProcessStatus.REFUND_REQUIRED, savedEvent().getProcessStatus());
    }

    @Test
    void handleCallback_refundsPaymentWhenNotAllSeatsRemainLocked() {
        Order order = order(OrderStatus.PENDING_PAYMENT, 2, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, PaymentStatus.PAYING, paidAt().plusMinutes(3));
        ShowtimeSeat remainingSeat = lockedSeat(order);
        stubNewEventFlow(order, payment);
        when(showtimeSeatRepository.findLockedByOrderIdForUpdate(ORDER_ID))
                .thenReturn(List.of(remainingSeat));

        paymentCallbackService.handleCallback(successCallback());

        assertEquals(PaymentStatus.REFUNDING, payment.getStatus());
        assertEquals(OrderStatus.REFUNDING, order.getStatus());
        assertAvailable(remainingSeat);
        assertEquals(PaymentCallbackProcessStatus.REFUND_REQUIRED, savedEvent().getProcessStatus());
    }

    @Test
    void handleCallback_refundsPaymentWhenNoSeatsRemainLocked() {
        Order order = order(OrderStatus.PENDING_PAYMENT, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, PaymentStatus.PAYING, paidAt().plusMinutes(3));
        stubNewEventFlow(order, payment);
        when(showtimeSeatRepository.findLockedByOrderIdForUpdate(ORDER_ID)).thenReturn(List.of());

        paymentCallbackService.handleCallback(successCallback());

        assertEquals(PaymentStatus.REFUNDING, payment.getStatus());
        assertEquals(OrderStatus.REFUNDING, order.getStatus());
        assertEquals(PaymentCallbackProcessStatus.REFUND_REQUIRED, savedEvent().getProcessStatus());
    }

    @Test
    void handleCallback_fillsProviderTradeNumberWhenLocalValueIsMissing() {
        Order order = order(OrderStatus.PENDING_PAYMENT, 1, paidAt().plusMinutes(5));
        PaymentTransaction payment = payment(order, PaymentStatus.PAYING, paidAt().plusMinutes(3));
        payment.setProviderTradeNo(null);
        ShowtimeSeat seat = lockedSeat(order);
        stubNewEventFlow(order, payment);
        when(showtimeSeatRepository.findLockedByOrderIdForUpdate(ORDER_ID)).thenReturn(List.of(seat));

        paymentCallbackService.handleCallback(successCallback());

        assertEquals(PROVIDER_TRADE_NO, payment.getProviderTradeNo());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void handleCallback_throwsWhenPaymentCannotBeFound() {
        when(paymentTransactionRepository.findOrderIdByPaymentNo(PAYMENT_NO))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> paymentCallbackService.handleCallback(successCallback())
        );

        assertEquals("Payment transaction not found", exception.getMessage());
        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(callbackEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handleCallback_throwsWhenOrderCannotBeFound() {
        when(paymentTransactionRepository.findOrderIdByPaymentNo(PAYMENT_NO))
                .thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> paymentCallbackService.handleCallback(successCallback())
        );

        assertEquals("Order not found", exception.getMessage());
        verify(callbackEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void handleCallback_throwsWhenPaymentDisappearsAfterOrderLookup() {
        Order order = order(OrderStatus.PENDING_PAYMENT, 1, paidAt().plusMinutes(5));
        when(paymentTransactionRepository.findOrderIdByPaymentNo(PAYMENT_NO))
                .thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(callbackEventRepository.findByChannelAndEventId(CHANNEL, EVENT_ID))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.findByPaymentNoForUpdate(PAYMENT_NO))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> paymentCallbackService.handleCallback(successCallback())
        );

        assertEquals("Payment transaction not found", exception.getMessage());
        verify(callbackEventRepository, never()).saveAndFlush(any());
    }

    private void stubNewEventFlow(Order order, PaymentTransaction payment) {
        when(paymentTransactionRepository.findOrderIdByPaymentNo(PAYMENT_NO))
                .thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(callbackEventRepository.findByChannelAndEventId(CHANNEL, EVENT_ID))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.findByPaymentNoForUpdate(PAYMENT_NO))
                .thenReturn(Optional.of(payment));
        when(callbackEventRepository.saveAndFlush(any(PaymentCallbackEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubExistingEventFlow(Order order, PaymentCallbackEvent event) {
        when(paymentTransactionRepository.findOrderIdByPaymentNo(PAYMENT_NO))
                .thenReturn(Optional.of(ORDER_ID));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(callbackEventRepository.findByChannelAndEventId(CHANNEL, EVENT_ID))
                .thenReturn(Optional.of(event));
    }

    private PaymentCallbackEvent savedEvent() {
        ArgumentCaptor<PaymentCallbackEvent> captor = ArgumentCaptor.forClass(PaymentCallbackEvent.class);
        verify(callbackEventRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private Order order(OrderStatus status, int seatCount, LocalDateTime expiresAt) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setCode("odr_test");
        order.setStatus(status);
        order.setExpiresAt(expiresAt);
        order.setTotalPrice(AMOUNT);
        for (int index = 0; index < seatCount; index++) {
            order.getOrderSeats().add(new OrderSeat());
        }
        return order;
    }

    private PaymentTransaction payment(Order order, PaymentStatus status, LocalDateTime expiresAt) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setId(200L);
        payment.setPaymentNo(PAYMENT_NO);
        payment.setOrder(order);
        payment.setChannel(CHANNEL);
        payment.setAmount(AMOUNT);
        payment.setStatus(status);
        payment.setProviderTradeNo(PROVIDER_TRADE_NO);
        payment.setExpiresAt(expiresAt);
        payment.setFailureCode("OLD_FAILURE");
        payment.setFailureMessage("Old failure");
        return payment;
    }

    private ShowtimeSeat lockedSeat(Order order) {
        ShowtimeSeat seat = new ShowtimeSeat();
        seat.setStatus(SeatStatus.LOCKED);
        seat.setOrder(order);
        seat.setLockToken("lock-token");
        seat.setLockUntil(order.getExpiresAt());
        return seat;
    }

    private PaymentCallbackEvent existingEvent(PaymentCallbackProcessStatus processStatus) {
        PaymentCallbackEvent event = new PaymentCallbackEvent();
        event.setChannel(CHANNEL);
        event.setEventId(EVENT_ID);
        event.setPaymentNo(PAYMENT_NO);
        event.setProviderTradeNo(PROVIDER_TRADE_NO);
        event.setAmount(AMOUNT);
        event.setCallbackStatus(PaymentCallbackStatus.SUCCESS);
        event.setProcessStatus(processStatus);
        return event;
    }

    private VerifiedPaymentCallback successCallback() {
        return callback(
                PaymentCallbackStatus.SUCCESS,
                AMOUNT,
                PROVIDER_TRADE_NO,
                PAID_AT,
                null,
                null
        );
    }

    private VerifiedPaymentCallback failedCallback() {
        return callback(
                PaymentCallbackStatus.FAILED,
                AMOUNT,
                PROVIDER_TRADE_NO,
                null,
                "INSUFFICIENT_BALANCE",
                "Insufficient balance"
        );
    }

    private VerifiedPaymentCallback callback(
            PaymentCallbackStatus status,
            BigDecimal amount,
            String providerTradeNo,
            Instant paidAt,
            String failureCode,
            String failureMessage
    ) {
        return new VerifiedPaymentCallback(
                EVENT_ID,
                CHANNEL,
                PAYMENT_NO,
                providerTradeNo,
                amount,
                status,
                paidAt,
                failureCode,
                failureMessage,
                RAW_PAYLOAD
        );
    }

    private LocalDateTime paidAt() {
        return LocalDateTime.ofInstant(PAID_AT, ZoneId.systemDefault());
    }

    private void assertSold(ShowtimeSeat seat, Order order) {
        assertEquals(SeatStatus.SOLD, seat.getStatus());
        assertSame(order, seat.getOrder());
        assertNull(seat.getLockToken());
        assertNull(seat.getLockUntil());
    }

    private void assertAvailable(ShowtimeSeat seat) {
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        assertNull(seat.getOrder());
        assertNull(seat.getLockToken());
        assertNull(seat.getLockUntil());
    }
}
