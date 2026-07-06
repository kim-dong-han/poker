package com.homepoker.web.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 특정 관찰자(viewer) 관점의 테이블 전체 상태. 상대 홀카드는 절대 담기지 않는다(SeatView 참고).
 * viewer* 필드는 "지금 이 화면 주인"이 취할 수 있는 액션 정보다.
 */
public record TableStateView(
        String tableId,
        boolean handInProgress,
        String street,
        List<String> board,
        long pot,
        List<PotView> pots,
        List<SeatView> seats,
        String currentActorId,
        Set<String> viewerLegalActions,
        long viewerToCall,
        long viewerMinRaiseTo,
        Map<String, Long> payouts
) {
}
