package me.wly.movie_reservation.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.wly.movie_reservation.order.model.OrderStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {
    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderExpirationService orderExpirationService;

    @Scheduled(
            fixedDelayString = "${order.expire-scan-delay-ms:60000}",
            initialDelayString = "${order.expire-scan-initial-delay-ms:10000}"
    )
    public void expireOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> orderIds = orderRepository.findExpiredOrderIds(
                OrderStatus.PENDING_PAYMENT,
                now,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (Long orderId : orderIds) {
            try {
                orderExpirationService.expireOne(orderId, now);
            } catch (RuntimeException exception) {
                log.error("Failed to expire order id={}", orderId, exception);
            }
        }
    }
}
