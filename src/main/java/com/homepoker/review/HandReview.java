package com.homepoker.review;

import java.util.List;

/**
 * 완료된 한 핸드의 자동 복기 결과.
 * 바둑 패착 감지와 같은 UX: 지점별 판정 전체 + "최대 실수 1개"를 따로 짚어준다.
 *
 * @param decisions     콜/폴드 지점별 판정(액션 순서대로, 모든 플레이어 포함)
 * @param worstMistake  EV 손실이 가장 큰 실수(실수가 하나도 없으면 null)
 * @param totalEvLossBb 이 핸드에서 발생한 실수들의 EV 손실 합(bb)
 * @param assumption    판정 전제(상대 레인지 가정) — 프론트에 그대로 노출해 과장 없이 알린다
 */
public record HandReview(
        List<DecisionReview> decisions,
        DecisionReview worstMistake,
        double totalEvLossBb,
        String assumption
) {
}
