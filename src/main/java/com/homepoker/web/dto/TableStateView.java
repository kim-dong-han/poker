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
        Map<String, Long> payouts,
        /** 보는 사람 본인의 몬테카를로 이퀴티(0~1). 계산 대상이 아니면 null. 상대 것은 절대 담기지 않는다. */
        Double viewerEquity,
        /** 현재 액션자의 제한시간 남은 초(타임뱅크). 대기자가 없으면 0. */
        long turnSecondsLeft
) {
}
