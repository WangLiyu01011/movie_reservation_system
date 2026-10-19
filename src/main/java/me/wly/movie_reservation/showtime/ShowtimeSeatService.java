package me.wly.movie_reservation.showtime;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderSeat;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class ShowtimeSeatService {
    private final ShowtimeSeatRepository showtimeSeatRepository;

    /** 行锁持有者为调用方法的订单创建事务 */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ShowtimeSeat> findAvailableForUpdate(Long showtimeId, List<Long> seatIds) {
        if (seatIds.isEmpty() || seatIds.size() != new HashSet<>(seatIds).size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Duplicate seats are not allowed");
        }
        List<ShowtimeSeat> seats = showtimeSeatRepository.findAllForUpdate(showtimeId, seatIds);
        if (seats.size() != seatIds.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "One or more seats do not belong to this showtime");
        }
        if (seats.stream().anyMatch(seat -> seat.getStatus() != SeatStatus.AVAILABLE || seat.getOrder() != null)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "One or more seats are unavailable");
        }
        return seats;
    }

    /** 数据库生成到期时间完成刷新后调用该方法 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForOrder(Order order, List<ShowtimeSeat> seats) {
        if (order.getId() == null || order.getExpiresAt() == null
                || order.getRequestId() == null || order.getRequestId().isBlank()
                || notMatchesOrderSeats(order, seats)
                || seats.stream().anyMatch(seat -> seat.getStatus() != SeatStatus.AVAILABLE
                        || seat.getOrder() != null
                        || !Objects.equals(seat.getShowtime().getId(), order.getShowtime().getId()))) {
            throw new IllegalStateException("Cannot lock seats for this order");
        }
        for (ShowtimeSeat seat : seats) {
            seat.setStatus(SeatStatus.LOCKED);
            seat.setOrder(order);
            seat.setLockToken(order.getRequestId());
            seat.setLockUntil(order.getExpiresAt());
        }
    }

    /** 调用该方法的事务必须已经持有订单锁 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseLockedForOrder(Order order) {
        List<ShowtimeSeat> lockedSeats = showtimeSeatRepository.findLockedByOrderIdForUpdate(order.getId());
        for (ShowtimeSeat seat : lockedSeats) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setOrder(null);
            seat.setLockToken(null);
            seat.setLockUntil(null);
        }
    }

    /** 如果有座位不再由调用订单持有锁，对所有座位不作修改并返回false */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markSoldForOrder(Order order) {
        List<ShowtimeSeat> lockedSeats = showtimeSeatRepository.findLockedByOrderIdForUpdate(order.getId());
        if (notMatchesOrderSeats(order, lockedSeats)
                || lockedSeats.stream().anyMatch(seat -> seat.getStatus() != SeatStatus.LOCKED
                        || seat.getOrder() == null
                        || !Objects.equals(seat.getOrder().getId(), order.getId()))) {
            return false;
        }
        for (ShowtimeSeat seat : lockedSeats) {
            seat.setStatus(SeatStatus.SOLD);
            seat.setLockToken(null);
            seat.setLockUntil(null);
        }
        return true;
    }

    /** Only a confirmed full refund may release sold seats, while holding the order lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseSoldForOrder(Order order) {
        List<ShowtimeSeat> seats = showtimeSeatRepository.findSoldByOrderIdForUpdate(order.getId());
        if (notMatchesOrderSeats(order, seats) || seats.stream().anyMatch(seat ->
                seat.getStatus() != SeatStatus.SOLD || seat.getOrder() == null
                        || !Objects.equals(seat.getOrder().getId(), order.getId()))) {
            throw new IllegalStateException("Refunded order no longer owns all sold seats");
        }
        for (ShowtimeSeat seat : seats) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setOrder(null);
            seat.setLockToken(null);
            seat.setLockUntil(null);
        }
    }

    private boolean notMatchesOrderSeats(Order order, List<ShowtimeSeat> seats) {
        if (seats.isEmpty() || seats.size() != order.getOrderSeats().size()) {
            return true;
        }
        Set<Long> expectedIds = new HashSet<>();
        for (OrderSeat orderSeat : order.getOrderSeats()) {
            if (orderSeat.getShowtimeSeat() == null || orderSeat.getShowtimeSeat().getId() == null
                    || !expectedIds.add(orderSeat.getShowtimeSeat().getId())) {
                return true;
            }
        }
        Set<Long> actualIds = new HashSet<>();
        for (ShowtimeSeat seat : seats) {
            if (seat.getId() == null || !actualIds.add(seat.getId())) {
                return true;
            }
        }
        return !expectedIds.equals(actualIds);
    }
}
