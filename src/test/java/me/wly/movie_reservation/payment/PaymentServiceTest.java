package me.wly.movie_reservation.payment;

import jakarta.persistence.EntityManager;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.order.OrderExpirationService;
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
    private OrderExpirationService orderExpirationService;
    @Mock
    private MockPaymentGateway mockPaymentGateway;
    @Mock
    private EntityManager entityManager;
    @InjectMocks
    private PaymentService paymentService;

    @Test
    void createPayment_usesServerSideOrderAmount() {
        User user = user(10L, "customer");
        Order order = order(500L, "odr_test", user, LocalDateTime.now().plusMinutes(5));
        PaymentRequestDTO dto = new PaymentRequestDTO("odr_test", " alipay ", "payment-request-1");
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByCodeAndUserIdForUpdate("odr_test", 10L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findByOrder_IdAndRequestId(500L, "payment-request-1"))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.findFirstByOrder_IdAndStatusInOrderByIdDesc(
                eq(500L), eq(List.of(PaymentStatus.CREATED, PaymentStatus.PAYING))))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction payment = invocation.getArgument(0);
            payment.setId(700L);
            payment.setPaymentNo("pay_test");
            payment.setExpiresAt(order.getExpiresAt().plusMinutes(1));
            return payment;
        });
        when(mockPaymentGateway.createPayment(any(PaymentCreateCommand.class)))
                .thenReturn(new PaymentCreateResult(
                        "mock_trade_test",
                        "http://localhost:8080/api/v1/mock-payments/pay_test",
                        order.getExpiresAt()
                ));

        PaymentRequestVO result = paymentService.createPayment(dto, "customer");

        ArgumentCaptor<PaymentTransaction> paymentCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).saveAndFlush(paymentCaptor.capture());
        PaymentTransaction payment = paymentCaptor.getValue();
        assertSame(order, payment.getOrder());
        assertEquals("payment-request-1", payment.getRequestId());
        assertEquals("ALIPAY", payment.getChannel());
        assertEquals(new BigDecimal("100.00"), payment.getAmount());
        assertEquals(PaymentStatus.PAYING, payment.getStatus());
        assertEquals("mock_trade_test", payment.getProviderTradeNo());
        assertEquals("http://localhost:8080/api/v1/mock-payments/pay_test", payment.getPayUrl());
        assertEquals("pay_test", result.paymentNo());
        assertEquals(new BigDecimal("100.00"), result.amount());
        assertEquals(PaymentStatus.PAYING, result.status());
        assertEquals(order.getExpiresAt(), result.expiresAt());
        assertEquals("http://localhost:8080/api/v1/mock-payments/pay_test", result.payUrl());
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

        PaymentRequestVO result = paymentService.createPayment(
                new PaymentRequestDTO("odr_test", "WECHAT", "payment-request-1"), "customer"
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

        PaymentRequestVO result = paymentService.createPayment(
                new PaymentRequestDTO("odr_test", "WECHAT", "new-request"), "customer"
        );

        assertEquals("pay_active", result.paymentNo());
        assertEquals(PaymentStatus.PAYING, result.status());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void createPayment_delegatesExpiredOrderToExpirationService() {
        User user = user(10L, "customer");
        Order order = order(500L, "odr_expired", user, LocalDateTime.now().minusSeconds(1));
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByCodeAndUserIdForUpdate("odr_expired", 10L)).thenReturn(Optional.of(order));
        when(orderExpirationService.expireLockedOrder(eq(order), any(LocalDateTime.class))).thenReturn(true);

        assertThrows(BusinessException.class, () -> paymentService.createPayment(
                new PaymentRequestDTO("odr_expired", "ALIPAY", "new-request"), "customer"
        ));

        verify(orderExpirationService).expireLockedOrder(eq(order), any(LocalDateTime.class));
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
