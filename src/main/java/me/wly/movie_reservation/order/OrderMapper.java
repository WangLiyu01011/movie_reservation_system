package me.wly.movie_reservation.order;

import me.wly.movie_reservation.order.model.Order;
import me.wly.movie_reservation.order.model.OrderSeat;
import me.wly.movie_reservation.order.vo.OrderCreateVO;
import me.wly.movie_reservation.order.vo.OrderSeatVO;
import me.wly.movie_reservation.order.vo.OrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(source = "orderSeats", target = "seatLocations")
    OrderVO entityToVO(Order order);
    List<OrderVO> toVOList(List<Order> orders);

    @Mapping(source = "code", target = "orderCode")
    @Mapping(source = "orderSeats", target = "seats")
    OrderCreateVO toCreateVO(Order order);

    @Mapping(source = "showtimeSeat.seatLabelSnapshot", target = "seatLabel")
    @Mapping(source = "showtimeSeat.seatTypeSnapshot", target = "seatType")
    OrderSeatVO toOrderSeatVO(OrderSeat orderSeat);

    default String toSeatLocation(OrderSeat orderSeat) {
        return orderSeat.getShowtimeSeat().getSeatLabelSnapshot();
    }
}
