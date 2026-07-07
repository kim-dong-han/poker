package com.homepoker.web.dto;

import java.util.List;
import java.util.Map;

/**
 * 리플레이의 한 프레임 = 액션 k개를 적용한 시점의 테이블 상태.
 * 지난 핸드이므로 모든 홀카드를 공개한다(리뷰용). action 은 이 프레임에 도달하려고 방금 적용한 액션(step 0 은 null).
 */
public record ReplayFrame(
        int step,
        String action,
        String street,
        List<String> board,
        long pot,
        List<PotView> pots,
        List<SeatView> seats,
        String actingId,
        Map<String, Long> payouts
) {
}
