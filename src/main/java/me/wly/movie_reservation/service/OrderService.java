package me.wly.movie_reservation.service;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.mapper.OrderMapper;
import me.wly.movie_reservation.model.dto.OrderGenerateDTO;
import me.wly.movie_reservation.model.entity.Order;
import me.wly.movie_reservation.model.entity.OrderSeat;
import me.wly.movie_reservation.model.entity.Showtime;
import me.wly.movie_reservation.model.entity.ShowtimeSeat;
import me.wly.movie_reservation.model.entity.User;
import me.wly.movie_reservation.model.enum_class.SeatStatus;
import me.wly.movie_reservation.model.vo.OrderVO;
import me.wly.movie_reservation.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final SeatRepository seatRepository;
    private final OrderSeatRepository orderSeatRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final ShowtimeRepository showtimeRepository;

    public List<OrderVO> getOrderByUserCode(String code) {
        User user = userRepository.getUserByCode(code);
        List<Order> orders = orderRepository.getOrdersByUser(user);
        return orderMapper.toVOList(orders);
    }

    @Transactional
    public String generateOrder(OrderGenerateDTO dto, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "User not found"));

        Order existingOrder = orderRepository.findByUser_IdAndRequestId(user.getId(), dto.requestId())
                .orElse(null);
        if (existingOrder != null) {
            return existingOrder.getCode();
        }

        Long showtimeId = dto.showtimeId();
        Showtime showtime = showtimeRepository.findById(dto.showtimeId())
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "Showtime not found"));

        List<Long> seatIds = dto.seatIds();
        if (seatIds.size() != new HashSet<>(seatIds).size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Duplicate seats are not allowed");
        }

        List<ShowtimeSeat> showtimeSeats = showtimeSeatRepository.findAllForUpdate(showtimeId, seatIds);
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

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(5);

        Order created = new Order();
        created.setUser(user);
        created.setRequestId(dto.requestId());
        created.setShowtime(showtime);
        created.setMovieTitle(showtime.getMovieTitle());
        created.setTheaterName(showtime.getTheaterName());
        created.setHallName(showtime.getHallName());
        created.setStartTime(showtime.getStartTime());
        created.setEndTime(showtime.getEndTime());
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        created.setExpiresAt(expiresAt);
        created.setTotalPrice(ticketPrice.multiply(BigDecimal.valueOf(showtimeSeats.size())));

        for (ShowtimeSeat showtimeSeat : showtimeSeats) {
            OrderSeat orderSeat = new OrderSeat();
            orderSeat.setShowtimeSeat(showtimeSeat);
            orderSeat.setTicketPrice(ticketPrice);
            created.addOrderSeat(orderSeat);
        }

        Order savedOrder = orderRepository.save(created);
        for (ShowtimeSeat showtimeSeat : showtimeSeats) {
            showtimeSeat.setStatus(SeatStatus.LOCKED);
            showtimeSeat.setOrder(savedOrder);
            showtimeSeat.setLockToken(dto.requestId());
            showtimeSeat.setLockUntil(expiresAt);
        }

        return savedOrder.getCode();
    }
}
