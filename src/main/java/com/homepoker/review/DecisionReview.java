package com.homepoker.review;

/**
 * 한 콜/폴드 의사결정 지점의 판정 결과.
 * 상대 홀카드는 랜덤 가정(몬테카를로)이며, GTO 판정이 아니라 "이퀴티 vs 팟오즈"라는
 * 수학적으로 반박 불가능한 비교만 한다.
 *
 * @param step           HandLog 액션 인덱스(리플레이 프레임 step+1 이 이 액션 직후 상태)
 * @param playerId       행동한 플레이어
 * @param playerName     표시 이름
 * @param street         결정 당시 스트리트(PREFLOP/FLOP/TURN/RIVER)
 * @param action         CALL 또는 FOLD
 * @param equity         결정 당시 몬테카를로 이퀴티(0~1, 상대 랜덤 가정)
 * @param requiredEquity 팟오즈로 요구되는 최소 이퀴티(0~1). toCall 0 인 폴드는 0
 * @param potBefore      결정 직전 팟(자기 커밋 포함 전체)
 * @param toCall         맞춰야 했던 금액(스택 부족 시 실제 넣을 수 있는 금액)
 * @param mistake        실수 여부(오차 여유폭을 넘어서 손해인 경우만 true)
 * @param evLossBb       실수일 때의 EV 손실(빅블라인드 단위, 실수 아니면 0)
 */
public record DecisionReview(
        int step,
        String playerId,
        String playerName,
        String street,
        String action,
        double equity,
        double requiredEquity,
        long potBefore,
        long toCall,
        boolean mistake,
        double evLossBb
) {
}
