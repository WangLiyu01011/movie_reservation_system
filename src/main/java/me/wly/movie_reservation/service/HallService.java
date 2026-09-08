package me.wly.movie_reservation.service;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.model.dto.HallCreateDTO;
import me.wly.movie_reservation.model.dto.HallDTO;
import me.wly.movie_reservation.model.dto.SeatCellCreateDTO;
import me.wly.movie_reservation.model.dto.SeatCellDTO;
import me.wly.movie_reservation.model.entity.Hall;
import me.wly.movie_reservation.model.entity.Seat;
import me.wly.movie_reservation.model.entity.Theater;
import me.wly.movie_reservation.model.enum_class.SeatType;
import me.wly.movie_reservation.repository.HallRepository;
import me.wly.movie_reservation.repository.SeatRepository;
import me.wly.movie_reservation.repository.TheaterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HallService {
    private final HallRepository hallRepository;
    private final TheaterRepository theaterRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public HallDTO createHall(HallCreateDTO dto) {
        Theater theater = theaterRepository.findById(dto.theaterId())
                .orElseThrow(() -> new BusinessException(
                        ResultCode.THEATER_NOT_FOUND,
                        "Target theater not found: " + dto.theaterId()
                ));
        if (hallRepository.existsByTheater_IdAndName(theater.getId(), dto.name())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Hall name already exists in this theater");
        }

        validateSeatLayout(dto);

        Hall hall = new Hall();
        hall.setTheater(theater);
        hall.setName(dto.name());
        hall.setType(dto.type());
        hall.setStatus("ACTIVE");
        hall.setRowCount(dto.rowCount());
        hall.setColumnCount(dto.columnCount());
        Hall savedHall = hallRepository.save(hall);

        List<Seat> seats = dto.seatLayout().stream()
                .map(cell -> createSeat(savedHall, cell))
                .toList();
        List<Seat> savedSeats = seatRepository.saveAll(seats);

        return new HallDTO(
                savedHall.getId(),
                theater.getId(),
                savedHall.getName(),
                savedHall.getType(),
                savedHall.getStatus(),
                savedHall.getRowCount(),
                savedHall.getColumnCount(),
                savedSeats.stream().map(this::toSeatCellDTO).toList()
        );
    }

    private void validateSeatLayout(HallCreateDTO dto) {
        int expectedCellCount = dto.rowCount() * dto.columnCount();
        if (expectedCellCount > 1000 || dto.seatLayout().size() != expectedCellCount) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Seat layout must contain every cell in the hall");
        }

        Set<String> positions = new HashSet<>();
        boolean hasSellableSeat = false;
        for (SeatCellCreateDTO cell : dto.seatLayout()) {
            if (cell.row() > dto.rowCount() || cell.column() > dto.columnCount()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "Seat position is outside hall dimensions");
            }
            String position = cell.row() + ":" + cell.column();
            if (!positions.add(position)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "Duplicate seat position: " + position);
            }
            if (cell.seatType() != SeatType.EMPTY) {
                hasSellableSeat = true;
            }
        }
        if (!hasSellableSeat) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Hall must contain at least one sellable seat");
        }
    }

    private Seat createSeat(Hall hall, SeatCellCreateDTO cell) {
        Seat seat = new Seat();
        seat.setHall(hall);
        seat.setX(cell.row());
        seat.setY(cell.column());
        seat.setSeatType(cell.seatType());
        seat.setSeatLabel(toSeatLabel(cell.row(), cell.column()));
        return seat;
    }

    private SeatCellDTO toSeatCellDTO(Seat seat) {
        return new SeatCellDTO(
                seat.getId(),
                seat.getX(),
                seat.getY(),
                seat.getSeatLabel(),
                seat.getSeatType()
        );
    }

    private String toSeatLabel(int row, int column) {
        StringBuilder rowLabel = new StringBuilder();
        int remaining = row;
        while (remaining > 0) {
            remaining--;
            rowLabel.append((char) ('A' + remaining % 26));
            remaining /= 26;
        }
        return rowLabel.reverse().append(column).toString();
    }
}
