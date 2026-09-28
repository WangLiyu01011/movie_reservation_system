package me.wly.movie_reservation.order;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.order.dto.OrderCreateDTO;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderSeat;
import me.wly.movie_reservation.showtime.ShowtimeRepository;
import me.wly.movie_reservation.showtime.ShowtimeSeatRepository;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.Showtime;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import me.wly.movie_reservation.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderCreationService {
    private final Clock businessClock;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final OrderRepository orderRepository;
    private final ShowtimeRepository showtimeRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Optional<Order> findExisting(OrderCreateDTO dto, Long userId) {
        return orderRepository.findByUser_IdAndRequestId(userId, dto.requestId())
                .map(order -> {
                    validateExistingRequest(order, dto);
                    return order;
                });
    }

    @Transactional
    public Order createOrder(OrderCreateDTO dto, User user) {
        Order existingOrder = orderRepository.findByUser_IdAndRequestId(user.getId(), dto.requestId())
                .orElse(null);

        if (existingOrder != null) {
            validateExistingRequest(existingOrder, dto);
            return existingOrder;
        }

        Showtime showtime = showtimeRepository.findById(dto.showtimeId())
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "Showtime not found"));
        if (showtime.getStartTime() == null || !showtime.getStartTime().isAfter(LocalDateTime.now(businessClock))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Cannot create an order for a started showtime");
        }

        List<Long> seatIds = dto.seatIds();
        if (seatIds.size() != new HashSet<>(seatIds).size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Duplicate seats are not allowed");
        }

        // 查询座位并为所有座位上悲观锁
        List<ShowtimeSeat> showtimeSeats = showtimeSeatRepository.findAllForUpdate(showtime.getId(), seatIds);
        if (showtimeSeats.size() != seatIds.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "One or more seats do not belong to this showtime");
        }
        if (showtimeSeats.stream().anyMatch(seat -> seat.getStatus() != SeatStatus.AVAILABLE)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "One or more seats are unavailable");
        }

        BigDecimal ticketPrice = showtime.getPrice();
        if (ticketPrice == null || ticketPrice.signum() < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Showtime price is invalid");
        }


        Order created = new Order();
        created.setUser(user);
        created.setRequestId(dto.requestId());
        created.setShowtime(showtime);
        created.setMovieTitle(showtime.getMovieTitle());
        created.setTheaterName(showtime.getTheaterName());
        created.setHallName(showtime.getHallName());
        created.setStartTime(showtime.getStartTime());
        created.setEndTime(showtime.getEndTime());
        created.setTotalPrice(ticketPrice.multiply(BigDecimal.valueOf(showtimeSeats.size())));

        for (ShowtimeSeat showtimeSeat : showtimeSeats) {
            OrderSeat orderSeat = new OrderSeat();
            orderSeat.setShowtimeSeat(showtimeSeat);
            orderSeat.setTicketPrice(ticketPrice);
            created.addOrderSeat(orderSeat);
        }

        Order savedOrder = orderRepository.saveAndFlush(created);
        entityManager.refresh(savedOrder);
        LocalDateTime expiresAt = savedOrder.getExpiresAt();
        for (ShowtimeSeat showtimeSeat : showtimeSeats) {
            showtimeSeat.setStatus(SeatStatus.LOCKED);
            showtimeSeat.setOrder(savedOrder);
            showtimeSeat.setLockToken(dto.requestId());
            showtimeSeat.setLockUntil(expiresAt);
        }

        return savedOrder;
    }

    // 判断是否存在requestId重复
    private void validateExistingRequest(Order order, OrderCreateDTO dto) {
        boolean sameShowtime = order.getShowtime() != null
                && Objects.equals(order.getShowtime().getId(), dto.showtimeId());
        List<Long> existingSeatIds = order.getOrderSeats().stream()
                .map(orderSeat -> orderSeat.getShowtimeSeat().getSeat().getId())
                .toList();
        boolean sameSeats = existingSeatIds.size() == dto.seatIds().size()
                && new HashSet<>(existingSeatIds).equals(new HashSet<>(dto.seatIds()));
        if (!sameShowtime || !sameSeats) {
            throw new BusinessException(ResultCode.IDEMPOTENCY_CONFLICT,
                    "This requestId was already used with a different showtime or seat selection");
        }
    }
}
