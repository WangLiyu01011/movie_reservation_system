package me.wly.movie_reservation.order;

import jakarta.persistence.EntityManager;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.order.dto.OrderCreateDTO;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderStatus;
import me.wly.movie_reservation.order.model.OrderSeat;
import me.wly.movie_reservation.showtime.ShowtimeRepository;
import me.wly.movie_reservation.showtime.ShowtimeSeatRepository;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.Showtime;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import me.wly.movie_reservation.theater.model.Seat;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreationServiceTest {
    private java.time.Clock businessClock = java.time.Clock.fixed(
            java.time.Instant.parse("2026-09-28T04:00:00Z"),
            me.wly.movie_reservation.common.time.BusinessTimeConfiguration.BUSINESS_ZONE);

    @Mock
    private ShowtimeSeatRepository showtimeSeatRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private EntityManager entityManager;
    private OrderCreationService orderService;
    @org.junit.jupiter.api.BeforeEach
    void setUpClock() {
        orderService = new OrderCreationService(businessClock, showtimeSeatRepository,
                orderRepository, showtimeRepository, entityManager);
    }

    @Test
    void createOrder_calculatesAmountCreatesOrderSeatsAndLocksShowtimeSeats() {
        User user = user(10L, "customer");
        Showtime showtime = showtime(20L, new BigDecimal("50.00"));
        ShowtimeSeat a1 = showtimeSeat(101L, showtime, 1L, SeatStatus.AVAILABLE);
        ShowtimeSeat a2 = showtimeSeat(102L, showtime, 2L, SeatStatus.AVAILABLE);
        OrderCreateDTO dto = new OrderCreateDTO(20L, List.of(1L, 2L), "order-request-1");
        LocalDateTime expiresAt = LocalDateTime.now(businessClock).plusMinutes(5);
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
        Order result = orderService.createOrder(dto, user);

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
        assertSame(savedOrder, result);
    }

    @Test
    void createOrder_returnsExistingOrderForSameRequestId() {
        User user = user(10L, "customer");
        Order existingOrder = order(500L, "odr_existing", user, OrderStatus.PENDING_PAYMENT,
                LocalDateTime.now(businessClock).plusMinutes(5));
        when(orderRepository.findByUser_IdAndRequestId(10L, "same-request")).thenReturn(Optional.of(existingOrder));

        Order result = orderService.createOrder(
                new OrderCreateDTO(20L, List.of(1L), "same-request"), user
        );

        assertSame(existingOrder, result);
        verifyNoInteractions(showtimeRepository, showtimeSeatRepository);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createOrder_rejectsDuplicateSeatIdsBeforeLockingSeats() {
        User user = user(10L, "customer");
        Showtime showtime = showtime(20L, new BigDecimal("50.00"));
        when(orderRepository.findByUser_IdAndRequestId(10L, "request-1")).thenReturn(Optional.empty());
        when(showtimeRepository.findById(20L)).thenReturn(Optional.of(showtime));

        assertThrows(BusinessException.class, () -> orderService.createOrder(
                new OrderCreateDTO(20L, List.of(1L, 1L), "request-1"), user
        ));

        verifyNoInteractions(showtimeSeatRepository);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createOrder_rejectsUnavailableSeat() {
        User user = user(10L, "customer");
        Showtime showtime = showtime(20L, new BigDecimal("50.00"));
        ShowtimeSeat unavailable = showtimeSeat(101L, showtime, 1L, SeatStatus.LOCKED);
        when(orderRepository.findByUser_IdAndRequestId(10L, "request-1")).thenReturn(Optional.empty());
        when(showtimeRepository.findById(20L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllForUpdate(20L, List.of(1L))).thenReturn(List.of(unavailable));

        assertThrows(BusinessException.class, () -> orderService.createOrder(
                new OrderCreateDTO(20L, List.of(1L), "request-1"), user
        ));

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void findExisting_rejectsRequestIdReuseForDifferentShowtime() {
        Order existing = order(500L, "odr_existing", user(10L, "customer"),
                OrderStatus.PENDING_PAYMENT, LocalDateTime.now(businessClock).plusMinutes(5));
        when(orderRepository.findByUser_IdAndRequestId(10L, "same-request")).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class, () -> orderService.findExisting(
                new OrderCreateDTO(21L, List.of(1L), "same-request"), 10L));

        assertEquals(ResultCode.IDEMPOTENCY_CONFLICT, exception.resultCode);
    }

    @Test
    void findExisting_rejectsRequestIdReuseForDifferentSeats() {
        Order existing = order(500L, "odr_existing", user(10L, "customer"),
                OrderStatus.PENDING_PAYMENT, LocalDateTime.now(businessClock).plusMinutes(5));
        when(orderRepository.findByUser_IdAndRequestId(10L, "same-request")).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class, () -> orderService.findExisting(
                new OrderCreateDTO(20L, List.of(2L), "same-request"), 10L));

        assertEquals(ResultCode.IDEMPOTENCY_CONFLICT, exception.resultCode);
    }

    @Test
    void findExisting_allowsSameSeatsInDifferentOrder() {
        Order existing = order(500L, "odr_existing", user(10L, "customer"),
                OrderStatus.PENDING_PAYMENT, LocalDateTime.now(businessClock).plusMinutes(5));
        OrderSeat second = new OrderSeat();
        second.setShowtimeSeat(showtimeSeat(102L, existing.getShowtime(), 2L, SeatStatus.LOCKED));
        existing.addOrderSeat(second);
        when(orderRepository.findByUser_IdAndRequestId(10L, "same-request")).thenReturn(Optional.of(existing));

        assertSame(existing, orderService.findExisting(
                new OrderCreateDTO(20L, List.of(2L, 1L), "same-request"), 10L).orElseThrow());
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
        showtime.setStartTime(LocalDateTime.now(businessClock).plusHours(2));
        showtime.setEndTime(LocalDateTime.now(businessClock).plusHours(4));
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
        Showtime showtime = showtime(20L, BigDecimal.TEN);
        order.setShowtime(showtime);
        OrderSeat orderSeat = new OrderSeat();
        orderSeat.setShowtimeSeat(showtimeSeat(101L, showtime, 1L, SeatStatus.LOCKED));
        order.addOrderSeat(orderSeat);
        return order;
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
