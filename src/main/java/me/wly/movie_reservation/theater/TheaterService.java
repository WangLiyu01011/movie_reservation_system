package me.wly.movie_reservation.theater;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.theater.dto.TheaterCreateDTO;
import me.wly.movie_reservation.theater.model.Area;
import me.wly.movie_reservation.theater.model.Theater;
import me.wly.movie_reservation.theater.vo.TheaterCreateVO;
import me.wly.movie_reservation.theater.vo.TheaterVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TheaterService {
    private final TheaterRepository theaterRepository;
    private final AreaRepository areaRepository;

    @Transactional
    public TheaterCreateVO createTheater(TheaterCreateDTO dto) {
        Area city = areaRepository.findById(dto.cityId())
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "City not found: " + dto.cityId()));
        Area district = areaRepository.findById(dto.districtId())
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "District not found: " + dto.districtId()));

        if (city.getLevel() != 1 || district.getLevel() != 2 || district.getParentId() != city.getId()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "District must belong to the selected city");
        }
        if (theaterRepository.existsByDistrict_IdAndTheaterName(district.getId(), dto.theaterName())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Theater name already exists in this district");
        }

        Theater theater = new Theater();
        theater.setCity(city);
        theater.setDistrict(district);
        theater.setTheaterName(dto.theaterName());
        theater.setLocation(dto.location());

        Theater savedTheater = theaterRepository.save(theater);
        return toCreateVO(savedTheater);
    }

    public List<TheaterVO> getTheaters(Integer cityId, Integer districtId){

        if(districtId!=null){
            List<Theater> theaters = theaterRepository.findTheaterByDistrict_Id(districtId);
            return theaters.stream().map(entity->new TheaterVO(entity.getTheaterName(), entity.getLocation()))
                    .toList();
        }

        if(cityId!=null){
            List<Theater> theaters = theaterRepository.findTheaterByCity_Id(cityId);
            return theaters.stream().map(entity->new TheaterVO(entity.getTheaterName(), entity.getLocation()))
                    .toList();
        }

        List<Theater> theaters = theaterRepository.findAll();
        return theaters.stream().map(entity->new TheaterVO(entity.getTheaterName(), entity.getLocation()))
                .toList();
    }

    private TheaterCreateVO toCreateVO(Theater theater) {
        return new TheaterCreateVO(
                theater.getId(),
                theater.getCity().getId(),
                theater.getCity().getName(),
                theater.getDistrict().getId(),
                theater.getDistrict().getName(),
                theater.getTheaterName(),
                theater.getLocation()
        );
    }

}
