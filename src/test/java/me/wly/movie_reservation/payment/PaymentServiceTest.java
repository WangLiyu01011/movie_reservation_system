package me.wly.movie_reservation.payment;

import me.wly.movie_reservation.common.exception.BusinessException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ShowtimeSeatRepository showtimeSeatRepository;
    @InjectMocks
    private PaymentService paymentService;

    @Test
    void createPayment_usesServerSideOrderAmount() {
        User user = user(10L, "customer");
        Order order = order(500L, "odr_test", user, LocalDateTime.now().plusMinutes(5));
        OrderPayDTO dto = new OrderPayDTO("odr_test", " alipay ", "payment-request-1");
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByCodeAndUserIdForUpdate("odr_test", 10L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findByOrder_IdAndRequestId(500L, "payment-request-1"))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.findFirstByOrder_IdAndStatusInOrderByIdDesc(
                eq(500L), eq(List.of(PaymentStatus.CREATED, PaymentStatus.PAYING))))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction payment = invocation.getArgument(0);
            payment.setId(700L);
            payment.setPaymentNo("pay_test");
            return payment;
        });

        OrderPayVO result = paymentService.createPayment(dto, "customer");

        ArgumentCaptor<PaymentTransaction> paymentCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(paymentCaptor.capture());
        PaymentTransaction payment = paymentCaptor.getValue();
        assertSame(order, payment.getOrder());
        assertEquals("payment-request-1", payment.getRequestId());
        assertEquals("ALIPAY", payment.getChannel());
        assertEquals(new BigDecimal("100.00"), payment.getAmount());
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
        assertEquals("pay_test", result.paymentNo());
        assertEquals(new BigDecimal("100.00"), result.amount());
    }

    @Test
    void createPayment_returnsSamePaymentForRepeatedRequestId() {
        User user = user(10L, "customer");
        Order order = order(500L, "odr_test", user, LocalDateTime.now().plusMinutes(5));
        PaymentTransaction existing = payment(700L, "pay_existing", order, "payment-request-1", PaymentStatus.CREATED);
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByCodeAndUserIdForUpdate("odr_test", 10L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findByOrder_IdAndRequestId(500L, "payment-request-1"))
                .thenReturn(Optional.of(existing));

        OrderPayVO result = paymentService.createPayment(
                new OrderPayDTO("odr_test", "WECHAT", "payment-request-1"), "customer"
        );

        assertEquals("pay_existing", result.paymentNo());
        assertEquals("ALIPAY", result.channel());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void createPayment_reusesExistingActivePaymentForDifferentRequestId() {
        User user = user(10L, "customer");
        Order order = order(500L, "odr_test", user, LocalDateTime.now().plusMinutes(5));
        PaymentTransaction active = payment(701L, "pay_active", order, "old-request", PaymentStatus.PAYING);
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByCodeAndUserIdForUpdate("odr_test", 10L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findByOrder_IdAndRequestId(500L, "new-request"))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.findFirstByOrder_IdAndStatusInOrderByIdDesc(
                eq(500L), eq(List.of(PaymentStatus.CREATED, PaymentStatus.PAYING))))
                .thenReturn(Optional.of(active));

        OrderPayVO result = paymentService.createPayment(
                new OrderPayDTO("odr_test", "WECHAT", "new-request"), "customer"
        );

        assertEquals("pay_active", result.paymentNo());
        assertEquals(PaymentStatus.PAYING, result.status());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void createPayment_expiresOrderReleasesSeatsAndClosesActivePayments() {
        User user = user(10L, "customer");
        Order order = order(500L, "odr_expired", user, LocalDateTime.now().minusSeconds(1));
        ShowtimeSeat lockedSeat = new ShowtimeSeat();
        lockedSeat.setStatus(SeatStatus.LOCKED);
        lockedSeat.setOrder(order);
        lockedSeat.setLockToken("order-request");
        lockedSeat.setLockUntil(order.getExpiresAt());
        PaymentTransaction active = payment(701L, "pay_active", order, "old-request", PaymentStatus.PAYING);
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByCodeAndUserIdForUpdate("odr_expired", 10L)).thenReturn(Optional.of(order));
        when(showtimeSeatRepository.findLockedByOrderIdForUpdate(500L)).thenReturn(List.of(lockedSeat));
        when(paymentTransactionRepository.findAllByOrder_IdAndStatusIn(
                eq(500L), eq(List.of(PaymentStatus.CREATED, PaymentStatus.PAYING))))
                .thenReturn(List.of(active));

        assertThrows(BusinessException.class, () -> paymentService.createPayment(
                new OrderPayDTO("odr_expired", "ALIPAY", "new-request"), "customer"
        ));

        assertEquals(OrderStatus.EXPIRED, order.getStatus());
        assertEquals(SeatStatus.AVAILABLE, lockedSeat.getStatus());
        assertNull(lockedSeat.getOrder());
        assertNull(lockedSeat.getLockToken());
        assertNull(lockedSeat.getLockUntil());
        assertEquals(PaymentStatus.CLOSED, active.getStatus());
        verify(paymentTransactionRepository, never()).save(any());
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private Order order(Long id, String code, User user, LocalDateTime expiresAt) {
        Order order = new Order();
        order.setId(id);
        order.setCode(code);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setExpiresAt(expiresAt);
        order.setTotalPrice(new BigDecimal("100.00"));
        return order;
    }

    private PaymentTransaction payment(
            Long id,
            String paymentNo,
            Order order,
            String requestId,
            PaymentStatus status
    ) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setId(id);
        payment.setPaymentNo(paymentNo);
        payment.setOrder(order);
        payment.setRequestId(requestId);
        payment.setChannel("ALIPAY");
        payment.setAmount(order.getTotalPrice());
        payment.setStatus(status);
        return payment;
    }
}
