package com.homepoker.review;

import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.HandLog;
import com.homepoker.engine.game.Player;
import com.homepoker.engine.game.PlayerStatus;
import com.homepoker.equity.EquityService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 핸드 자동 복기 — EV 손실 기반 실수(패착) 감지.
 *
 * 완료된 핸드의 이벤트 소싱 기록(HandLog)을 되감으며, 각 콜/폴드 지점에서
 * "그때의 몬테카를로 이퀴티 vs 팟오즈"를 비교해 EV 손실을 수치화한다.
 *
 * 스코프(터지지 않게): 레이즈 사이즈·블러프 빈도 같은 GTO 영역은 판정하지 않는다.
 * 상대 홀카드는 랜덤 가정임을 결과에 명시한다.
 */
@Service
public class HandReviewer {

    /** 복기용 몬테카를로 반복 횟수(핸드 종료 후 1회성 계산이라 라이브보다 넉넉히). */
    private static final int DEFAULT_ITERATIONS = 3000;

    /** 몬테카를로 오차·근사 가정 흡수용 여유폭. 이퀴티 차이가 이 이내면 실수로 치지 않는다. */
    static final double MARGIN = 0.02;

    static final String ASSUMPTION = "상대 홀카드는 랜덤 가정(몬테카를로). 팟오즈 대비 이퀴티만 판정하며 GTO 판정이 아님";

    private final EquityService equityService;

    public HandReviewer(EquityService equityService) {
        this.equityService = equityService;
    }

    public HandReview review(HandLog log) {
        return review(log, DEFAULT_ITERATIONS, new Random());
    }

    /** 반복 횟수·난수원 주입 버전(테스트 재현성). */
    public HandReview review(HandLog log, int iterations, Random rng) {
        List<DecisionReview> decisions = new ArrayList<>();
        for (int i = 0; i < log.actionCount(); i++) {
            Action a = log.actions().get(i);
            if (a.type() != ActionType.CALL && a.type() != ActionType.FOLD) {
                continue; // 벳/레이즈 사이즈 적정성은 스코프 밖, 체크는 공짜라 판정 대상 아님
            }
            HandEngine e = log.stateAt(i); // 이 액션 직전 상태 복원
            Player actor = e.playerToAct();
            if (actor == null || !actor.id().equals(a.playerId()) || actor.holeCards().size() != 2) {
                continue;
            }
            int opponents = (int) e.players().stream()
                    .filter(p -> !p.id().equals(actor.id()) && p.status() != PlayerStatus.FOLDED)
                    .count();
            if (opponents < 1) {
                continue;
            }
            // 스택 부족 시 실제로 넣을 수 있는 만큼만이 진짜 콜 비용(올인 콜)
            long toCall = Math.min(e.amountToCall(actor.id()), actor.stack());
            double equity = equityService
                    .estimate(actor.holeCards(), e.board(), opponents, iterations, rng)
                    .equity();
            decisions.add(judge(i, actor.id(), actor.name(), e.street().name(), a.type(),
                    equity, e.pot(), toCall, log.bigBlind()));
        }

        DecisionReview worst = decisions.stream()
                .filter(DecisionReview::mistake)
                .max(Comparator.comparingDouble(DecisionReview::evLossBb))
                .orElse(null);
        double totalLoss = decisions.stream()
                .filter(DecisionReview::mistake)
                .mapToDouble(DecisionReview::evLossBb)
                .sum();
        return new HandReview(List.copyOf(decisions), worst, totalLoss, ASSUMPTION);
    }

    /**
     * 순수 판정 함수(테스트 가능하도록 몬테카를로와 분리).
     *
     * 필요이퀴티 = toCall / (potBefore + toCall). 콜 EV = equity×(팟+콜) − 콜 이므로
     *  - 콜인데 이퀴티 < 필요치  → 손실 = (필요치 − 이퀴티) × (팟+콜)
     *  - 폴드인데 이퀴티 > 필요치 → 손실 = (이퀴티 − 필요치) × (팟+콜)  (버린 콜 EV)
     * 경계(정확히 팟오즈)는 실수가 아니며, MARGIN 이내의 차이도 실수로 치지 않는다.
     */
    static DecisionReview judge(int step, String playerId, String playerName, String street,
                                ActionType action, double equity, long potBefore, long toCall,
                                long bigBlind) {
        double required = toCall <= 0 ? 0.0 : (double) toCall / (potBefore + toCall);
        double gap = action == ActionType.CALL ? required - equity : equity - required;
        boolean mistake = gap > MARGIN;
        double evLossBb = mistake ? gap * (potBefore + toCall) / bigBlind : 0.0;
        return new DecisionReview(step, playerId, playerName, street, action.name(),
                equity, required, potBefore, toCall, mistake, evLossBb);
    }
}
