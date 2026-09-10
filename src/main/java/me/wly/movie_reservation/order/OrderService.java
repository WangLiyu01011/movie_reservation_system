package me.wly.movie_reservation.order;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.order.dto.OrderCreateDTO;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderSeat;
import me.wly.movie_reservation.order.vo.OrderCreateVO;
import me.wly.movie_reservation.order.vo.OrderVO;
import me.wly.movie_reservation.showtime.ShowtimeRepository;
import me.wly.movie_reservation.showtime.ShowtimeSeatRepository;
import me.wly.movie_reservation.showtime.model.SeatStatus;
import me.wly.movie_reservation.showtime.model.Showtime;
import me.wly.movie_reservation.showtime.model.ShowtimeSeat;
import me.wly.movie_reservation.user.UserRepository;
import me.wly.movie_reservation.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final ShowtimeRepository showtimeRepository;
    private final EntityManager entityManager;

    public List<OrderVO> getOrderByUsername(String username) {
        User user = userRepository.getUserByUsername(username);
        List<Order> orders = orderRepository.getOrdersByUser(user);
        return orderMapper.toVOList(orders);
    }

    @Transactional
    public OrderCreateVO createOrder(OrderCreateDTO dto, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "User not found"));

        Order existingOrder = orderRepository.findByUser_IdAndRequestId(user.getId(), dto.requestId())
                .orElse(null);
        if (existingOrder != null) {
            return orderMapper.toCreateVO(existingOrder);
        }

        Showtime showtime = showtimeRepository.findById(dto.showtimeId())
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "Showtime not found"));
        if (showtime.getStartTime() == null || !showtime.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Cannot create an order for a started showtime");
        }

        List<Long> seatIds = dto.seatIds();
        if (seatIds.size() != new HashSet<>(seatIds).size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Duplicate seats are not allowed");
        }

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

        return orderMapper.toCreateVO(created);
    }
}
