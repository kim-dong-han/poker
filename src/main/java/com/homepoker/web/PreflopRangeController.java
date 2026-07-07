package com.homepoker.web;

import com.homepoker.range.Position;
import com.homepoker.range.PreflopRangeService;
import com.homepoker.web.dto.RangeAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * 프리플랍 오픈 레인지 오버레이 REST 엔드포인트.
 *  - GET /api/preflop-range                         : 포지션별 전체 오픈 레인지
 *  - GET /api/preflop-range/{position}              : 한 포지션의 오픈 핸드 집합
 *  - GET /api/preflop-range/{position}/advice?hand= : 그 핸드가 표준 오픈인지 조언
 */
@RestController
public class PreflopRangeController {

    private final PreflopRangeService rangeService;

    public PreflopRangeController(PreflopRangeService rangeService) {
        this.rangeService = rangeService;
    }

    @GetMapping("/api/preflop-range")
    public Map<Position, Set<String>> all() {
        return rangeService.all();
    }

    @GetMapping("/api/preflop-range/{position}")
    public Set<String> forPosition(@PathVariable Position position) {
        return rangeService.openingRange(position);
    }

    @GetMapping("/api/preflop-range/{position}/advice")
    public RangeAdvice advice(@PathVariable Position position, @RequestParam String hand) {
        return new RangeAdvice(position.name(), hand, rangeService.shouldOpen(position, hand));
    }
}
