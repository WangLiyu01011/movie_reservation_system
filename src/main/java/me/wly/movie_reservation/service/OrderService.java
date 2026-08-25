package me.wly.movie_reservation.service;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.mapper.OrderMapper;
import me.wly.movie_reservation.model.entity.Order;
import me.wly.movie_reservation.model.entity.User;
import me.wly.movie_reservation.model.vo.OrderVO;
import me.wly.movie_reservation.repository.OrderRepository;
import me.wly.movie_reservation.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    public List<OrderVO> getOrderByUserCode(String code) {
        User user = userRepository.getUserByCode(code);
        List<Order> orders = orderRepository.getOrdersByUser(user);
        return orderMapper.toVOList(orders);
    }
}
