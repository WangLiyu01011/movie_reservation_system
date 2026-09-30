package me.wly.movie_reservation.showtime;

import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderSeat;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.Showtime;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowtimeSeatServiceTest {
    @Mock private ShowtimeSeatRepository repository;

    @Test
    void lockForOrder_rejectsAnotherSeatWithoutChangingState() {
        ShowtimeSeat expected = seat(1L, SeatStatus.AVAILABLE);
        ShowtimeSeat different = seat(2L, SeatStatus.AVAILABLE);
        Order order = order(expected);

        assertThrows(IllegalStateException.class,
                () -> service().lockForOrder(order, List.of(different)));

        assertEquals(SeatStatus.AVAILABLE, different.getStatus());
        assertNull(different.getOrder());
        assertNull(different.getLockToken());
    }

    @Test
    void markSoldForOrder_rejectsSameCountWithDifferentSeatWithoutPartialUpdate() {
        ShowtimeSeat expected = seat(1L, SeatStatus.LOCKED);
        ShowtimeSeat different = seat(2L, SeatStatus.LOCKED);
        Order order = order(expected);
        different.setOrder(order);
        different.setLockToken("token");
        when(repository.findLockedByOrderIdForUpdate(order.getId())).thenReturn(List.of(different));

        assertFalse(service().markSoldForOrder(order));

        assertEquals(SeatStatus.LOCKED, different.getStatus());
        assertEquals("token", different.getLockToken());
    }

    @Test
    void releaseLockedForOrder_canBeRepeatedAfterSeatsAreReleased() {
        ShowtimeSeat seat = seat(1L, SeatStatus.LOCKED);
        Order order = order(seat);
        seat.setOrder(order);
        seat.setLockToken("token");
        seat.setLockUntil(order.getExpiresAt());
        when(repository.findLockedByOrderIdForUpdate(order.getId()))
                .thenReturn(List.of(seat), List.of());

        ShowtimeSeatService service = service();
        service.releaseLockedForOrder(order);
        service.releaseLockedForOrder(order);

        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        assertNull(seat.getOrder());
        assertNull(seat.getLockToken());
        assertNull(seat.getLockUntil());
        verify(repository, times(2)).findLockedByOrderIdForUpdate(order.getId());
    }

    private ShowtimeSeatService service() {
        return new ShowtimeSeatService(repository);
    }

    private Order order(ShowtimeSeat seat) {
        Order order = new Order();
        order.setId(100L);
        order.setRequestId("request-1");
        order.setExpiresAt(LocalDateTime.of(2030, 1, 1, 12, 0));
        Showtime showtime = new Showtime();
        showtime.setId(20L);
        order.setShowtime(showtime);
        OrderSeat orderSeat = new OrderSeat();
        orderSeat.setShowtimeSeat(seat);
        order.addOrderSeat(orderSeat);
        return order;
    }

    private ShowtimeSeat seat(Long id, SeatStatus status) {
        ShowtimeSeat seat = new ShowtimeSeat();
        seat.setId(id);
        Showtime showtime = new Showtime();
        showtime.setId(20L);
        seat.setShowtime(showtime);
        seat.setStatus(status);
        return seat;
    }
}
