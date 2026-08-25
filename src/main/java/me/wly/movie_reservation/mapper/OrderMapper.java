package me.wly.movie_reservation.mapper;

import me.wly.movie_reservation.model.entity.Order;
import me.wly.movie_reservation.model.vo.OrderVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderVO entityToVO(Order order);
    List<OrderVO> toVOList(List<Order> orders);
}
