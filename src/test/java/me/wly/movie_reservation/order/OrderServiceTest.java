package me.wly.movie_reservation.order;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.RateLimitException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.order.dto.OrderCreateDTO;
import me.wly.movie_reservation.order.model.OrderStatus;
import me.wly.movie_reservation.order.vo.OrderCreateVO;
import me.wly.movie_reservation.user.UserRepository;
import me.wly.movie_reservation.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private OrderCreationService orderCreationService;
    @Mock private OrderCacheService orderCacheService;
    @InjectMocks private OrderService orderService;

    private User user;
    private final OrderCreateDTO dto = new OrderCreateDTO(20L, List.of(1L), "request-1");
    private final OrderCreateVO result = new OrderCreateVO("odr_test", OrderStatus.PENDING_PAYMENT,
            BigDecimal.TEN, null, null, null, null, null, null, List.of());

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(10L);
    }

    private OrderCacheService.RequestPermit prepare(OrderCacheService.RequestState state) {
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderCreationService.findExisting(dto, 10L)).thenReturn(Optional.empty());
        var permit = new OrderCacheService.RequestPermit("order:idem:{10}:request-1", "token", state);
        when(orderCacheService.acquireRequest(10L, "request-1")).thenReturn(permit);
        return permit;
    }

    @Test
    void createOrder_returnsCommittedOrderWithoutRedisOrRateLimit() {
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(user));
        when(orderCreationService.findExisting(dto, 10L)).thenReturn(Optional.of(result));

        assertSame(result, orderService.createOrder(dto, "customer"));
        verifyNoInteractions(orderCacheService);
        verify(orderCreationService, never()).createOrder(any(), any());
    }

    @Test
    void createOrder_checksRateThenCreatesAndReleasesPermit() {
        var permit = prepare(OrderCacheService.RequestState.ACQUIRED);
        when(orderCreationService.createOrder(dto, user)).thenReturn(result);

        assertSame(result, orderService.createOrder(dto, "customer"));

        var sequence = inOrder(orderCacheService, orderCreationService);
        sequence.verify(orderCreationService).findExisting(dto, 10L);
        sequence.verify(orderCacheService).acquireRequest(10L, "request-1");
        sequence.verify(orderCreationService).findExisting(dto, 10L);
        sequence.verify(orderCacheService).checkRateLimit(10L);
        sequence.verify(orderCreationService).createOrder(dto, user);
        sequence.verify(orderCacheService).releaseRequest(permit);
        verify(userRepository).findByUsername("customer");
    }

    @Test
    void createOrder_rejectsInFlightDuplicateWithoutRateLimit() {
        var permit = prepare(OrderCacheService.RequestState.BUSY);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.createOrder(dto, "customer"));

        assertEquals(ResultCode.REQUEST_IN_PROGRESS, exception.resultCode);
        verify(orderCacheService, never()).checkRateLimit(any());
        verify(orderCreationService, never()).createOrder(any(), any());
        verify(orderCacheService).releaseRequest(permit);
    }

    @Test
    void createOrder_replaysOrderThatCommitsWhileAcquiringPermit() {
        var permit = prepare(OrderCacheService.RequestState.BUSY);
        when(orderCreationService.findExisting(dto, 10L))
                .thenReturn(Optional.empty(), Optional.of(result));

        assertSame(result, orderService.createOrder(dto, "customer"));
        verify(orderCacheService, never()).checkRateLimit(any());
        verify(orderCreationService, never()).createOrder(any(), any());
        verify(orderCacheService).releaseRequest(permit);
    }

    @Test
    void createOrder_releasesPermitWhenRateLimited() {
        var permit = prepare(OrderCacheService.RequestState.ACQUIRED);
        doThrow(new RateLimitException(1500)).when(orderCacheService).checkRateLimit(10L);

        assertThrows(RateLimitException.class, () -> orderService.createOrder(dto, "customer"));
        verify(orderCreationService, never()).createOrder(any(), any());
        verify(orderCacheService).releaseRequest(permit);
    }

    @Test
    void createOrder_releasesPermitAfterCreationFailure() {
        var permit = prepare(OrderCacheService.RequestState.ACQUIRED);
        BusinessException failure = new BusinessException(ResultCode.BAD_REQUEST, "Seat unavailable");
        when(orderCreationService.createOrder(dto, user)).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class,
                () -> orderService.createOrder(dto, "customer")));
        verify(orderCacheService).releaseRequest(permit);
    }

    @Test
    void createOrder_usesDatabaseCreationWhenRedisIsUnavailable() {
        prepare(OrderCacheService.RequestState.REDIS_UNAVAILABLE);
        when(orderCreationService.createOrder(dto, user)).thenReturn(result);

        assertSame(result, orderService.createOrder(dto, "customer"));
        verify(orderCreationService).createOrder(dto, user);
    }

    @Test
    void createOrder_replaysConcurrentDatabaseWinnerAfterUniqueConflict() {
        var permit = prepare(OrderCacheService.RequestState.REDIS_UNAVAILABLE);
        when(orderCreationService.findExisting(dto, 10L))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(result));
        when(orderCreationService.createOrder(dto, user))
                .thenThrow(new DataIntegrityViolationException("Duplicate user/request"));

        assertSame(result, orderService.createOrder(dto, "customer"));
        verify(orderCacheService).releaseRequest(permit);
    }

    @Test
    void createOrder_rethrowsUnrelatedDatabaseConstraintFailure() {
        var permit = prepare(OrderCacheService.RequestState.ACQUIRED);
        var failure = new DataIntegrityViolationException("Other constraint");
        when(orderCreationService.createOrder(dto, user)).thenThrow(failure);

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> orderService.createOrder(dto, "customer")));
        verify(orderCacheService).releaseRequest(permit);
    }

    @Test
    void createOrder_rejectsDuplicateSeatsBeforeRedis() {
        assertThrows(BusinessException.class, () -> orderService.createOrder(
                new OrderCreateDTO(20L, List.of(1L, 1L), "request-1"), "customer"));
        verifyNoInteractions(userRepository, orderCreationService, orderCacheService);
    }
}
