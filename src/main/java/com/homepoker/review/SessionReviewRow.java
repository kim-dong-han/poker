package com.homepoker.review;

/**
 * 세션 누적 복기 리포트의 한 줄(플레이어별).
 * 예: "이번 세션 EV 손실 합계 -23bb, 최다 유형: RIVER CALL(리버 오버콜)".
 *
 * @param playerId        플레이어
 * @param playerName      표시 이름
 * @param decisions       판정된 콜/폴드 지점 수
 * @param mistakes        실수 판정 수
 * @param totalEvLossBb   세션 전체 EV 손실 합(bb)
 * @param topMistakeType  최다 실수 유형("스트리트 액션", 예: "RIVER CALL") — 실수 없으면 null
 */
public record SessionReviewRow(
        String playerId,
        String playerName,
        int decisions,
        int mistakes,
        double totalEvLossBb,
        String topMistakeType
) {
}
