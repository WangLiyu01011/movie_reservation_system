package me.wly.movie_reservation.service;

import jakarta.persistence.EntityManager;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.mapper.OrderMapper;
import me.wly.movie_reservation.model.dto.OrderCreateDTO;
import me.wly.movie_reservation.model.dto.OrderPayDTO;
import me.wly.movie_reservation.model.entity.Order;
import me.wly.movie_reservation.model.entity.PaymentTransaction;
import me.wly.movie_reservation.model.entity.Seat;
import me.wly.movie_reservation.model.entity.Showtime;
import me.wly.movie_reservation.model.entity.ShowtimeSeat;
import me.wly.movie_reservation.model.entity.User;
import me.wly.movie_reservation.model.enum_class.OrderStatus;
import me.wly.movie_reservation.model.enum_class.PaymentStatus;
import me.wly.movie_reservation.model.enum_class.SeatStatus;
import me.wly.movie_reservation.model.vo.OrderCreateVO;
import me.wly.movie_reservation.model.vo.OrderPayVO;
import me.wly.movie_reservation.repository.OrderRepository;
import me.wly.movie_reservation.repository.PaymentTransactionRepository;
import me.wly.movie_reservation.repository.ShowtimeRepository;
import me.wly.movie_reservation.repository.ShowtimeSeatRepository;
import me.wly.movie_reservation.repository.UserRepository;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private ShowtimeSeatRepository showtimeSeatRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private EntityManager entityManager;
    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_calculatesAmountCreatesOrderSeatsAndLocksShowtimeSeats() {
        User user = user(10L, "customer");
        Showtime showtime = showtime(20L, new BigDecimal("50.00"));
        ShowtimeSeat a1 = showtimeSeat(101L, showtime, 1L, SeatStatus.AVAILABLE);
        ShowtimeSeat a2 = showtimeSeat(102L, showtime, 2L, SeatStatus.AVAILABLE);
        OrderCreateDTO dto = new OrderCreateDTO(20L, List.of(1L, 2L), "order-request-1");
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        OrderCreateVO expectedVO = new OrderCreateVO(
                "odr_test", OrderStatus.PENDING_PAYMENT, new BigDecimal("100.00"), expiresAt,
                "测试电影", "测试影院", "一号厅", showtime.getStartTime(), showtime.getEndTime(), List.of()
        );

        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByUser_IdAndRequestId(10L, "order-request-1")).thenReturn(Optional.empty());
        when(showtimeRepository.findById(20L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllForUpdate(20L, List.of(1L, 2L))).thenReturn(List.of(a1, a2));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(500L);
            order.setCode("odr_test");
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            return order;
        });
        doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setExpiresAt(expiresAt);
            return null;
        }).when(entityManager).refresh(any(Order.class));
        when(orderMapper.toCreateVO(any(Order.class))).thenReturn(expectedVO);

        OrderCreateVO result = orderService.createOrder(dto, "customer");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertEquals(new BigDecimal("100.00"), savedOrder.getTotalPrice());
        assertEquals(2, savedOrder.getOrderSeats().size());
        assertSame(savedOrder, savedOrder.getOrderSeats().getFirst().getOrder());
        assertEquals(new BigDecimal("50.00"), savedOrder.getOrderSeats().getFirst().getTicketPrice());
        verify(entityManager).refresh(savedOrder);
        assertLockedByOrder(a1, savedOrder, "order-request-1", expiresAt);
        assertLockedByOrder(a2, savedOrder, "order-request-1", expiresAt);
        assertSame(expectedVO, result);
    }

    @Test
    void createOrder_returnsExistingOrderForSameRequestId() {
        User user = user(10L, "customer");
        Order existingOrder = order(500L, "odr_existing", user, OrderStatus.PENDING_PAYMENT,
                LocalDateTime.now().plusMinutes(5));
        OrderCreateVO expectedVO = new OrderCreateVO(
                existingOrder.getCode(), existingOrder.getStatus(), BigDecimal.TEN, existingOrder.getExpiresAt(),
                null, null, null, null, null, List.of()
        );
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByUser_IdAndRequestId(10L, "same-request")).thenReturn(Optional.of(existingOrder));
        when(orderMapper.toCreateVO(existingOrder)).thenReturn(expectedVO);

        OrderCreateVO result = orderService.createOrder(
                new OrderCreateDTO(20L, List.of(1L), "same-request"), "customer"
        );

        assertSame(expectedVO, result);
        verifyNoInteractions(showtimeRepository, showtimeSeatRepository);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createOrder_rejectsDuplicateSeatIdsBeforeLockingSeats() {
        User user = user(10L, "customer");
        Showtime showtime = showtime(20L, new BigDecimal("50.00"));
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByUser_IdAndRequestId(10L, "request-1")).thenReturn(Optional.empty());
        when(showtimeRepository.findById(20L)).thenReturn(Optional.of(showtime));

        assertThrows(BusinessException.class, () -> orderService.createOrder(
                new OrderCreateDTO(20L, List.of(1L, 1L), "request-1"), "customer"
        ));

        verifyNoInteractions(showtimeSeatRepository);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createOrder_rejectsUnavailableSeat() {
        User user = user(10L, "customer");
        Showtime showtime = showtime(20L, new BigDecimal("50.00"));
        ShowtimeSeat unavailable = showtimeSeat(101L, showtime, 1L, SeatStatus.LOCKED);
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByUser_IdAndRequestId(10L, "request-1")).thenReturn(Optional.empty());
        when(showtimeRepository.findById(20L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllForUpdate(20L, List.of(1L))).thenReturn(List.of(unavailable));

        assertThrows(BusinessException.class, () -> orderService.createOrder(
                new OrderCreateDTO(20L, List.of(1L), "request-1"), "customer"
        ));

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void payOrder_createsPaymentTransactionFromServerSideOrderAmount() {
        User user = user(10L, "customer");
        Order order = order(500L, "odr_test", user, OrderStatus.PENDING_PAYMENT,
                LocalDateTime.now().plusMinutes(5));
        order.setTotalPrice(new BigDecimal("100.00"));
        OrderPayDTO dto = new OrderPayDTO(" alipay ", "payment-request-1");
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

        OrderPayVO result = orderService.payOrder("odr_test", dto, "customer");

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
    void payOrder_returnsSamePaymentForRepeatedRequestId() {
        User user = user(10L, "customer");
        Order order = order(500L, "odr_test", user, OrderStatus.PENDING_PAYMENT,
                LocalDateTime.now().plusMinutes(5));
        order.setTotalPrice(new BigDecimal("100.00"));
        PaymentTransaction existing = payment(700L, "pay_existing", order, "payment-request-1", PaymentStatus.CREATED);
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByCodeAndUserIdForUpdate("odr_test", 10L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findByOrder_IdAndRequestId(500L, "payment-request-1"))
                .thenReturn(Optional.of(existing));

        OrderPayVO result = orderService.payOrder(
                "odr_test", new OrderPayDTO("WECHAT", "payment-request-1"), "customer"
        );

        assertEquals("pay_existing", result.paymentNo());
        assertEquals("ALIPAY", result.channel());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void payOrder_reusesExistingActivePaymentForDifferentRequestId() {
        User user = user(10L, "customer");
        Order order = order(500L, "odr_test", user, OrderStatus.PENDING_PAYMENT,
                LocalDateTime.now().plusMinutes(5));
        order.setTotalPrice(new BigDecimal("100.00"));
        PaymentTransaction active = payment(701L, "pay_active", order, "old-request", PaymentStatus.PAYING);
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderRepository.findByCodeAndUserIdForUpdate("odr_test", 10L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findByOrder_IdAndRequestId(500L, "new-request"))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.findFirstByOrder_IdAndStatusInOrderByIdDesc(
                eq(500L), eq(List.of(PaymentStatus.CREATED, PaymentStatus.PAYING))))
                .thenReturn(Optional.of(active));

        OrderPayVO result = orderService.payOrder(
                "odr_test", new OrderPayDTO("WECHAT", "new-request"), "customer"
        );

        assertEquals("pay_active", result.paymentNo());
        assertEquals(PaymentStatus.PAYING, result.status());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void payOrder_expiresOrderReleasesSeatsAndClosesActivePayments() {
        User user = user(10L, "customer");
        Order order = order(500L, "odr_expired", user, OrderStatus.PENDING_PAYMENT,
                LocalDateTime.now().minusSeconds(1));
        order.setTotalPrice(new BigDecimal("100.00"));
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

        assertThrows(BusinessException.class, () -> orderService.payOrder(
                "odr_expired", new OrderPayDTO("ALIPAY", "new-request"), "customer"
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

    private Showtime showtime(Long id, BigDecimal price) {
        Showtime showtime = new Showtime();
        showtime.setId(id);
        showtime.setPrice(price);
        showtime.setMovieTitle("测试电影");
        showtime.setTheaterName("测试影院");
        showtime.setHallName("一号厅");
        showtime.setStartTime(LocalDateTime.now().plusHours(2));
        showtime.setEndTime(LocalDateTime.now().plusHours(4));
        return showtime;
    }

    private ShowtimeSeat showtimeSeat(Long id, Showtime showtime, Long seatId, SeatStatus status) {
        Seat seat = new Seat();
        seat.setId(seatId);
        ShowtimeSeat showtimeSeat = new ShowtimeSeat();
        showtimeSeat.setId(id);
        showtimeSeat.setShowtime(showtime);
        showtimeSeat.setSeat(seat);
        showtimeSeat.setStatus(status);
        return showtimeSeat;
    }

    private Order order(Long id, String code, User user, OrderStatus status, LocalDateTime expiresAt) {
        Order order = new Order();
        order.setId(id);
        order.setCode(code);
        order.setUser(user);
        order.setStatus(status);
        order.setExpiresAt(expiresAt);
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

    private void assertLockedByOrder(
            ShowtimeSeat seat,
            Order order,
            String lockToken,
            LocalDateTime lockUntil
    ) {
        assertEquals(SeatStatus.LOCKED, seat.getStatus());
        assertSame(order, seat.getOrder());
        assertEquals(lockToken, seat.getLockToken());
        assertEquals(lockUntil, seat.getLockUntil());
    }
}
