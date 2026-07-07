package com.homepoker.web.dto;

import java.util.List;
import java.util.Map;

/**
 * 핸드 히스토리 목록의 한 줄. index 는 최신이 0(가장 최근 핸드).
 * 상세 리플레이는 GET /api/tables/{id}/hands/{index} 로 받는다.
 */
public record HandSummaryView(
        int index,
        List<String> players,
        int actionCount,
        List<String> finalBoard,
        Map<String, Long> payouts,
        boolean showdown
) {
}
