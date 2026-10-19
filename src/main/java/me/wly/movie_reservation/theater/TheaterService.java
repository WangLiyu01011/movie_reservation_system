package me.wly.movie_reservation.theater;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.theater.dto.TheaterCreateDTO;
import me.wly.movie_reservation.theater.dto.TheaterSearchResultDTO;
import me.wly.movie_reservation.theater.model.Area;
import me.wly.movie_reservation.theater.model.Theater;
import me.wly.movie_reservation.theater.vo.TheaterCreateVO;
import me.wly.movie_reservation.theater.vo.TheaterVO;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
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

    /** Bounded AI lookup; unlike the public list API, a resolved city is mandatory. */
    @Transactional(readOnly = true)
    public TheaterSearchResultDTO searchTheaters(Integer cityId, Integer districtId) {
        if (cityId == null || cityId <= 0 || (districtId != null && districtId <= 0)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "A valid cityId and optional districtId are required");
        }
        Area city = areaRepository.findById(cityId)
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "City not found"));
        if (city.getLevel() != 1) throw new BusinessException(ResultCode.BAD_REQUEST, "cityId must refer to a city");
        if (districtId != null) {
            Area district = areaRepository.findById(districtId)
                    .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "District not found"));
            if (district.getLevel() != 2 || district.getParentId() != city.getId()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "District must belong to the selected city");
            }
        }
        var rows = theaterRepository.findCandidates(cityId, districtId, PageRequest.of(0, 21));
        var items = rows.stream().limit(20).map(theater -> new TheaterVO(theater.getTheaterName(), theater.getLocation())).toList();
        return new TheaterSearchResultDTO(cityId, districtId, items, rows.size() > 20);
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
