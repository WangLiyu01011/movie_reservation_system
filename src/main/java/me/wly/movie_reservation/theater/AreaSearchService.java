package me.wly.movie_reservation.theater;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.theater.dto.AreaCandidateDTO;
import me.wly.movie_reservation.theater.dto.AreaSearchResultDTO;
import me.wly.movie_reservation.theater.model.Area;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AreaSearchService {
    private static final int MAX_CANDIDATES = 10;
    private final AreaRepository areas;

    @Transactional(readOnly = true)
    public AreaSearchResultDTO search(String keyword, Integer level, Integer parentId) {
        if (keyword == null || keyword.isBlank() || keyword.strip().length() > 32) {
            throw badRequest("Area keyword must contain 1 to 32 characters");
        }
        String name = keyword.strip();
        int targetLevel = level == null ? 1 : level;
        if (targetLevel != 1 && targetLevel != 2) throw badRequest("Area level must be 1 (city) or 2 (district/county)");
        if (parentId != null) {
            if (parentId <= 0) throw badRequest("parentId must be positive");
            var parent = areas.findById(parentId).orElseThrow(() -> badRequest("Parent area not found"));
            if (parent.getLevel() != targetLevel - 1) throw badRequest("Parent area has an incompatible level");
        }

        var limit = PageRequest.of(0, MAX_CANDIDATES + 1);
        List<Area> matches = areas.findExactCandidates((short) targetLevel, aliases(name, targetLevel), parentId, limit);
        if (matches.isEmpty()) {
            // Treat wildcard characters as literal input rather than allowing an unrestricted LIKE query.
            String pattern = name.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            matches = areas.findPrefixCandidates((short) targetLevel, pattern, parentId, limit);
        }
        boolean hasMore = matches.size() > MAX_CANDIDATES;
        var visible = matches.subList(0, Math.min(matches.size(), MAX_CANDIDATES));
        var parentIds = visible.stream().map(Area::getParentId).filter(id -> id > 0).collect(Collectors.toSet());
        var parents = parentIds.isEmpty() ? java.util.Map.<Integer, Area>of()
                : areas.findAllById(parentIds).stream().collect(Collectors.toMap(Area::getId, Function.identity()));
        var candidates = visible.stream().map(area -> new AreaCandidateDTO(area.getId(), area.getName().strip(),
                area.getLevel(), area.getParentId(), parents.containsKey(area.getParentId())
                ? parents.get(area.getParentId()).getName().strip() : null)).toList();
        var status = candidates.isEmpty() ? AreaSearchResultDTO.Status.NOT_FOUND
                : candidates.size() == 1 && !hasMore ? AreaSearchResultDTO.Status.MATCHED : AreaSearchResultDTO.Status.AMBIGUOUS;
        return new AreaSearchResultDTO(name, status, candidates, hasMore);
    }

    private Set<String> aliases(String name, int level) {
        var names = new LinkedHashSet<String>();
        names.add(name);
        // Append known suffixes; do not destructively strip names such as 沙市区 or 市中区.
        if (level == 1 && !name.endsWith("市")) names.add(name + "市");
        if (level == 2 && !name.endsWith("区") && !name.endsWith("县") && !name.endsWith("市") && !name.endsWith("旗")) {
            for (String suffix : List.of("区", "县", "市", "旗")) names.add(name + suffix);
        }
        return names;
    }

    private BusinessException badRequest(String message) { return new BusinessException(ResultCode.BAD_REQUEST, message); }
}
