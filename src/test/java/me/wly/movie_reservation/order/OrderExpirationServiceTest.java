package me.wly.movie_reservation.order;

import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderStatus;
import me.wly.movie_reservation.payment.PaymentTransactionRepository;
import me.wly.movie_reservation.showtime.ShowtimeSeatRepository;
import me.wly.movie_reservation.showtime.ShowtimeSeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private ShowtimeSeatRepository showtimeSeatRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;

    private OrderExpirationService service;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        service = new OrderExpirationService(
                orderRepository,
                paymentTransactionRepository,
                new ShowtimeSeatService(showtimeSeatRepository)
        );
        now = LocalDateTime.of(2026, 9, 24, 20, 0);
    }

    @Test
    void expireOne_expiresPendingOrderWhenDue() {
        Order order = order(1L, OrderStatus.PENDING_PAYMENT, now.minusSeconds(1));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(showtimeSeatRepository.findLockedByOrderIdForUpdate(1L)).thenReturn(List.of());
        when(paymentTransactionRepository.findAllByOrder_IdAndStatusIn(eq(1L), anyList()))
                .thenReturn(List.of());

        OrderExpirationResult result = service.expireOne(1L, now);

        assertEquals(OrderExpirationResult.EXPIRED, result);
        assertEquals(OrderStatus.EXPIRED, order.getStatus());
        assertEquals(now, order.getCancelledAt());
    }

    @Test
    void expireOne_reportsTerminalOrderAsRemovable() {
        Order order = order(2L, OrderStatus.PAID, now.minusMinutes(1));
        when(orderRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(order));

        assertEquals(OrderExpirationResult.ALREADY_TERMINAL, service.expireOne(2L, now));
        verifyNoInteractions(showtimeSeatRepository, paymentTransactionRepository);
    }

    @Test
    void expireOne_keepsPendingOrderThatIsNotDue() {
        Order order = order(3L, OrderStatus.PENDING_PAYMENT, now.plusMinutes(1));
        when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));

        assertEquals(OrderExpirationResult.NOT_DUE, service.expireOne(3L, now));
        verifyNoInteractions(showtimeSeatRepository, paymentTransactionRepository);
    }

    @Test
    void expireOne_reportsMissingOrderAsRemovable() {
        when(orderRepository.findByIdForUpdate(4L)).thenReturn(Optional.empty());

        assertEquals(OrderExpirationResult.NOT_FOUND, service.expireOne(4L, now));
        verifyNoInteractions(showtimeSeatRepository, paymentTransactionRepository);
    }

    private Order order(Long id, OrderStatus status, LocalDateTime expiresAt) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(status);
        order.setExpiresAt(expiresAt);
        return order;
    }
}
