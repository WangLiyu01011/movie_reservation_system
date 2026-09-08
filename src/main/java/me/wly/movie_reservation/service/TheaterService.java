package me.wly.movie_reservation.service;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.model.entity.Theater;
import me.wly.movie_reservation.model.vo.TheaterVO;
import me.wly.movie_reservation.repository.TheaterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TheaterService {
    private final TheaterRepository theaterRepository;

    public List<TheaterVO> getTheaters(Integer cityId, Integer districtId){

        if(districtId!=null){
            List<Theater> theaters = theaterRepository.findTheaterByDistrict_Id(districtId);
            return theaters.stream().map(entity->new TheaterVO(entity.getTheaterName(), entity.getLocation()))
                    .toList();
        }

        if(cityId!=null){
            cityId = (cityId % 100) * 100;
            List<Theater> theaters = theaterRepository.findTheaterByCity_Id(cityId);
            return theaters.stream().map(entity->new TheaterVO(entity.getTheaterName(), entity.getLocation()))
                    .toList();
        }

        List<Theater> theaters = theaterRepository.findAll();
        return theaters.stream().map(entity->new TheaterVO(entity.getTheaterName(), entity.getLocation()))
                .toList();
    }


}
