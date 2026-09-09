package me.wly.movie_reservation.service;

import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.model.dto.TheaterCreateDTO;
import me.wly.movie_reservation.model.entity.Area;
import me.wly.movie_reservation.model.entity.Theater;
import me.wly.movie_reservation.model.vo.TheaterCreateVO;
import me.wly.movie_reservation.repository.AreaRepository;
import me.wly.movie_reservation.repository.TheaterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TheaterServiceTest {
    @Mock
    private TheaterRepository theaterRepository;
    @Mock
    private AreaRepository areaRepository;
    @InjectMocks
    private TheaterService theaterService;

    @Test
    void createTheater_savesTheaterWithValidatedArea() {
        Area city = area(100, "杭州市", 10, (short) 1);
        Area district = area(101, "西湖区", 100, (short) 2);
        TheaterCreateDTO dto = new TheaterCreateDTO(100, 101, "西湖影城", "文三路 100 号");
        when(areaRepository.findById(100)).thenReturn(Optional.of(city));
        when(areaRepository.findById(101)).thenReturn(Optional.of(district));
        when(theaterRepository.existsByDistrict_IdAndTheaterName(101, "西湖影城")).thenReturn(false);
        when(theaterRepository.save(any(Theater.class))).thenAnswer(invocation -> {
            Theater theater = invocation.getArgument(0);
            theater.setId(501);
            return theater;
        });

        TheaterCreateVO result = theaterService.createTheater(dto);

        ArgumentCaptor<Theater> theaterCaptor = ArgumentCaptor.forClass(Theater.class);
        verify(theaterRepository).save(theaterCaptor.capture());
        Theater savedTheater = theaterCaptor.getValue();
        assertEquals(city, savedTheater.getCity());
        assertEquals(district, savedTheater.getDistrict());
        assertEquals(501, result.id());
        assertEquals("杭州市", result.cityName());
        assertEquals("西湖区", result.districtName());
    }

    @Test
    void createTheater_rejectsDistrictThatDoesNotBelongToCity() {
        Area city = area(100, "杭州市", 10, (short) 1);
        Area districtInAnotherCity = area(101, "西湖区", 999, (short) 2);
        when(areaRepository.findById(100)).thenReturn(Optional.of(city));
        when(areaRepository.findById(101)).thenReturn(Optional.of(districtInAnotherCity));

        assertThrows(BusinessException.class, () -> theaterService.createTheater(validDto()));

        verify(theaterRepository, never()).save(any());
    }

    @Test
    void createTheater_rejectsDuplicateNameInSameDistrict() {
        Area city = area(100, "杭州市", 10, (short) 1);
        Area district = area(101, "西湖区", 100, (short) 2);
        when(areaRepository.findById(100)).thenReturn(Optional.of(city));
        when(areaRepository.findById(101)).thenReturn(Optional.of(district));
        when(theaterRepository.existsByDistrict_IdAndTheaterName(eq(101), eq("西湖影城"))).thenReturn(true);

        assertThrows(BusinessException.class, () -> theaterService.createTheater(validDto()));

        verify(theaterRepository, never()).save(any());
    }

    private TheaterCreateDTO validDto() {
        return new TheaterCreateDTO(100, 101, "西湖影城", "文三路 100 号");
    }

    private Area area(int id, String name, int parentId, short level) {
        Area area = new Area();
        area.setId(id);
        area.setName(name);
        area.setParentId(parentId);
        area.setLevel(level);
        return area;
    }
}
