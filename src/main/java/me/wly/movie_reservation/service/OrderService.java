package me.wly.movie_reservation.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.mapper.OrderMapper;
import me.wly.movie_reservation.model.dto.OrderCreateDTO;
import me.wly.movie_reservation.model.dto.OrderPayDTO;
import me.wly.movie_reservation.model.entity.Order;
import me.wly.movie_reservation.model.entity.OrderSeat;
import me.wly.movie_reservation.model.entity.PaymentTransaction;
import me.wly.movie_reservation.model.entity.Showtime;
import me.wly.movie_reservation.model.entity.ShowtimeSeat;
import me.wly.movie_reservation.model.entity.User;
import me.wly.movie_reservation.model.enum_class.OrderStatus;
import me.wly.movie_reservation.model.enum_class.PaymentStatus;
import me.wly.movie_reservation.model.enum_class.SeatStatus;
import me.wly.movie_reservation.model.vo.OrderCreateVO;
import me.wly.movie_reservation.model.vo.OrderPayVO;
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
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final ShowtimeRepository showtimeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EntityManager entityManager;

    public List<OrderVO> getOrderByUserCode(String code) {
        User user = userRepository.getUserByCode(code);
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

    @Transactional(noRollbackFor = BusinessException.class)
    public OrderPayVO payOrder(String orderCode, OrderPayDTO dto, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "User not found"));
        Order order = orderRepository.findByCodeAndUserIdForUpdate(orderCode, user.getId())
                .orElseThrow(() -> new BusinessException(ResultCode.ORDER_NOT_FOUND, "Order not found"));

        LocalDateTime now = LocalDateTime.now();
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                && (order.getExpiresAt() == null || !order.getExpiresAt().isAfter(now))) {
            expireOrder(order, now);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Order cannot be paid in status: " + order.getStatus());
        }
        if (order.getTotalPrice() == null || order.getTotalPrice().signum() < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Order amount is invalid");
        }

        PaymentTransaction sameRequest = paymentTransactionRepository
                .findByOrder_IdAndRequestId(order.getId(), dto.requestId())
                .orElse(null);
        if (sameRequest != null) {
            return toOrderPayVO(sameRequest);
        }

        /* Avoid more than one PaymentTransaction related to one order */
        PaymentTransaction activePayment = paymentTransactionRepository
                .findFirstByOrder_IdAndStatusInOrderByIdDesc(order.getId(), List.of(PaymentStatus.CREATED, PaymentStatus.PAYING))
                .orElse(null);
        if (activePayment != null) {
            return toOrderPayVO(activePayment);
        }

        PaymentTransaction payment = new PaymentTransaction();
        payment.setOrder(order);
        payment.setRequestId(dto.requestId());
        payment.setChannel(dto.channel().trim().toUpperCase());
        payment.setAmount(order.getTotalPrice());
        payment.setStatus(PaymentStatus.CREATED);
        PaymentTransaction savedPayment = paymentTransactionRepository.save(payment);

        return toOrderPayVO(savedPayment);
    }

    private void expireOrder(Order order, LocalDateTime now) {
        order.setStatus(OrderStatus.EXPIRED);
        order.setCancelledAt(now);
        order.setUpdatedAt(now);

        List<ShowtimeSeat> lockedSeats = showtimeSeatRepository.findLockedByOrderIdForUpdate(order.getId());
        for (ShowtimeSeat seat : lockedSeats) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setOrder(null);
            seat.setLockToken(null);
            seat.setLockUntil(null);
        }

        List<PaymentTransaction> activePayments = paymentTransactionRepository.findAllByOrder_IdAndStatusIn(
                order.getId(), List.of(PaymentStatus.CREATED, PaymentStatus.PAYING)
        );
        activePayments.forEach(payment -> payment.setStatus(PaymentStatus.CLOSED));
    }

    private OrderPayVO toOrderPayVO(PaymentTransaction payment) {
        return new OrderPayVO(
                payment.getPaymentNo(),
                payment.getOrder().getCode(),
                payment.getChannel(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getOrder().getExpiresAt()
        );
    }
}
