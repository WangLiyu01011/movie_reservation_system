package me.wly.movie_reservation.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {
    private java.time.Clock businessClock = java.time.Clock.fixed(
            java.time.Instant.parse("2026-09-28T04:00:00Z"),
            me.wly.movie_reservation.common.time.BusinessTimeConfiguration.BUSINESS_ZONE);

    @Mock private OrderRepository orderRepository;
    @Mock private OrderExpirationService orderExpirationService;
    @Mock private OrderCacheService orderCacheService;
    private OrderExpirationScheduler scheduler;
    @org.junit.jupiter.api.BeforeEach
    void setUpClock() {
        scheduler = new OrderExpirationScheduler(businessClock, orderRepository, orderExpirationService, orderCacheService);
    }

    @Test
    void expireOrdersFromRedis_removesOnlyCompletedCandidates() {
        when(orderCacheService.findExpiredOrderIds()).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(orderExpirationService.expireOne(eq(1L), any(LocalDateTime.class)))
                .thenReturn(OrderExpirationResult.EXPIRED);
        when(orderExpirationService.expireOne(eq(2L), any(LocalDateTime.class)))
                .thenReturn(OrderExpirationResult.ALREADY_TERMINAL);
        when(orderExpirationService.expireOne(eq(3L), any(LocalDateTime.class)))
                .thenReturn(OrderExpirationResult.NOT_FOUND);
        when(orderExpirationService.expireOne(eq(4L), any(LocalDateTime.class)))
                .thenReturn(OrderExpirationResult.NOT_DUE);

        scheduler.expireOrdersFromRedis();

        verify(orderCacheService).deleteOrderFromZSet("1");
        verify(orderCacheService).deleteOrderFromZSet("2");
        verify(orderCacheService).deleteOrderFromZSet("3");
        verify(orderCacheService, never()).deleteOrderFromZSet("4");
    }

    @Test
    void expireOrdersFromRedis_keepsCandidateWhenDatabaseProcessingFails() {
        when(orderCacheService.findExpiredOrderIds()).thenReturn(List.of(5L));
        when(orderExpirationService.expireOne(eq(5L), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        scheduler.expireOrdersFromRedis();

        verify(orderCacheService, never()).deleteOrderFromZSet("5");
    }
}
