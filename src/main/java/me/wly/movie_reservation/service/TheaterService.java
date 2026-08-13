package me.wly.movie_reservation.service;

import me.wly.movie_reservation.model.entity.Theater;
import me.wly.movie_reservation.model.vo.TheaterCardVO;
import me.wly.movie_reservation.repository.TheaterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TheaterService {
    @Autowired
    TheaterRepository theaterRepository;

    public List<TheaterCardVO> getTheatersInCity(Integer id){
        List<Theater> theaters = theaterRepository.findTheaterByCity_Id(id);
        return theaters.stream().map(entity->new TheaterCardVO(entity.getTheaterName(), entity.getLocation(), entity.getHallTypesContain()))
                .toList();
    }

    public List<TheaterCardVO> getTheatersInDistrict(Integer id){
        List<Theater> theaters = theaterRepository.findTheaterByDistrict_Id(id);
        return theaters.stream().map(entity->new TheaterCardVO(entity.getTheaterName(), entity.getLocation(), entity.getHallTypesContain()))
                .toList();
    }

}
