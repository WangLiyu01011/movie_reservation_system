package me.wly.movie_reservation.order;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.order.dto.OrderCreateDTO;
import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.vo.OrderCreateVO;
import me.wly.movie_reservation.order.vo.OrderVO;
import me.wly.movie_reservation.user.UserRepository;
import me.wly.movie_reservation.user.model.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final OrderCreationService orderCreationService;
    private final OrderCacheService orderCacheService;

    @Transactional(readOnly = true)
    public List<OrderVO> getOrderByUsername(String username) {
        User user = userRepository.getUserByUsername(username);
        List<Order> orders = orderRepository.getOrdersByUser(user);
        return orderMapper.toVOList(orders);
    }

    /**
     创建订单流程：
        1.查询数据库是否已有同用户同RequestId订单
        2.尝试创建redis锁
        3.查询用户限流情况
        4.确认订单创建成功释放redis锁
     */
    public OrderCreateVO createOrder(OrderCreateDTO dto, String username) {
        // 判断是否有重复座位
        if (dto.seatIds().size() != new HashSet<>(dto.seatIds()).size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Duplicate seats are not allowed");
        }

        // 查询数据库获取用户id
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "User not found"));
        Optional<OrderCreateVO> existing = orderCreationService.findExisting(dto, user.getId());
        if (existing.isPresent()) {
            // 订单已成功创建入库，返回原订单
            return existing.get();
        }

        OrderCacheService.RequestPermit permit = orderCacheService.acquireRequest(user.getId(), dto.requestId());
        try {
            // 防止数据库查询和SET NX之间有相同订单写入
            existing = orderCreationService.findExisting(dto, user.getId());
            if (existing.isPresent()) {
                return existing.get();
            }
            if (permit.state() == OrderCacheService.RequestState.BUSY) {
                throw new BusinessException(ResultCode.REQUEST_IN_PROGRESS,
                        "This order request is being processed; retry with the same requestId");
            }

            // 确认请求限流限制，若超限抛出异常
            orderCacheService.checkRateLimit(user.getId());
            try {
                return orderCreationService.createOrder(dto, user);
            } catch (BusinessException | DataIntegrityViolationException exception) {
                // 抛出异常，创建事务rollback之前确认是否已有写入
                Optional<OrderCreateVO> committed = orderCreationService.findExisting(dto, user.getId());
                if (committed.isPresent()) {
                    return committed.get();
                }
                throw exception;
            }
        } finally {
            orderCacheService.releaseRequest(permit);
        }
    }
}
