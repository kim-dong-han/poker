package com.homepoker.bot;

import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.Player;
import com.homepoker.engine.game.PlayerStatus;
import com.homepoker.engine.game.Street;
import com.homepoker.equity.EquityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * AI 상대의 의사결정. 복기(HandReviewer)와 같은 철학 — GTO 흉내 없이
 * "몬테카를로 이퀴티 vs 팟오즈"라는 수학적 기준으로만 판단한다.
 * 그래서 봇의 플레이는 항상 복기 기능으로도 설명 가능하다(자기 기준으로 실수하지 않는 봇).
 *
 * 규칙(상대 홀카드 랜덤 가정):
 *  - 맞출 벳이 없으면: 이퀴티가 강하면 팟의 2/3 벳, 아니면 체크
 *  - 맞출 벳이 있으면: 필요이퀴티에 크게 못 미치면 폴드, 크게 상회하면 레이즈, 그 외 콜
 * 임계값에 소폭 난수 지터를 섞어 완전히 기계적인 착취를 어렵게 한다.
 */
@Service
public class BotBrain {

    /** 봇 판단용 몬테카를로 반복(수백 ms 내 응답을 위해 라이브 오버레이 수준). */
    private static final int DEFAULT_ITERATIONS = 1200;

    /**
     * 밸류벳 기준 배수: 공평 지분(1/총인원)의 이 배수를 넘는 이퀴티면 밸류벳.
     * 헤즈업이면 1.24 × 1/2 = 62%(기존 고정 기준과 동일), 상대가 늘수록 기준이 함께 내려간다.
     * 예) 8명 상대 AA 이퀴티 ≈ 34% 는 공평 지분 11% 의 3배가 넘는 압도적 우위인데,
     * 고정 62% 기준으로는 풀테이블에서 영원히 벳을 못 한다(림프-체크 파티의 원인).
     */
    static final double BET_SHARE_MULTIPLIER = 1.24;
    static final double FOLD_MARGIN = 0.03;     // 필요이퀴티보다 이만큼 낮으면 폴드
    static final double RAISE_MARGIN = 0.25;    // 필요이퀴티보다 이만큼 높으면 레이즈
    static final double JITTER = 0.03;          // 임계값 ±지터(예측 불가성)

    /**
     * 봇이 취할 액션. amount 는 BET/RAISE 일 때만 의미(raise-to 방식).
     * reason 은 판단 근거(이퀴티 vs 기준)를 사람이 읽을 수 있게 요약한 것 —
     * 봇 홀카드 강도가 드러나므로 핸드 진행 중에는 클라이언트에 내보내지 않는다.
     */
    public record Decision(String type, long amount, String reason) {}

    private final EquityService equityService;
    private final PreflopAdvisor preflopAdvisor;
    private final PostflopAdvisor postflopAdvisor;

    /** 차트·규칙 없이 이퀴티 로직만 쓰는 봇(기존 동작 그대로 — 테스트·차트 미보유 환경). */
    public BotBrain(EquityService equityService) {
        this(equityService, PreflopAdvisor.disabled(), PostflopAdvisor.disabled());
    }

    /** 프리플랍 차트만 켠 봇(차트 스모크 테스트 호환). */
    public BotBrain(EquityService equityService, PreflopAdvisor preflopAdvisor) {
        this(equityService, preflopAdvisor, PostflopAdvisor.disabled());
    }

    @Autowired
    public BotBrain(EquityService equityService, PreflopAdvisor preflopAdvisor,
                    PostflopAdvisor postflopAdvisor) {
        this.equityService = equityService;
        this.preflopAdvisor = preflopAdvisor;
        this.postflopAdvisor = postflopAdvisor;
    }

    public Decision decide(HandEngine engine, String botId) {
        return decide(engine, botId, DEFAULT_ITERATIONS, new Random());
    }

    /** 반복 횟수·난수원 주입 버전(테스트 재현성). */
    public Decision decide(HandEngine engine, String botId, int iterations, Random rng) {
        Set<ActionType> legal = engine.legalActions(botId);
        if (legal.isEmpty()) {
            throw new IllegalStateException("봇 차례가 아니다: " + botId);
        }
        // 프리플랍 = BTS 차트, 포스트플랍 = 해링턴 규칙 우선 — 둘 다 규칙 밖이면 이퀴티 폴백
        if (engine.street() == Street.PREFLOP) {
            Optional<Decision> byChart = preflopAdvisor.advise(engine, botId, rng);
            if (byChart.isPresent()) {
                return byChart.get();
            }
        } else {
            Optional<Decision> byRules = postflopAdvisor.advise(engine, botId, rng);
            if (byRules.isPresent()) {
                return byRules.get();
            }
        }
        Player me = engine.players().stream()
                .filter(p -> p.id().equals(botId)).findFirst().orElseThrow();
        int opponents = (int) engine.players().stream()
                .filter(p -> !p.id().equals(botId) && p.status() != PlayerStatus.FOLDED)
                .count();
        double equity = equityService
                .estimate(me.holeCards(), engine.board(), opponents, iterations, rng)
                .equity();
        double jitter = (rng.nextDouble() * 2 - 1) * JITTER;

        long pot = engine.pot();
        long toCall = Math.min(engine.amountToCall(botId), me.stack());

        if (toCall == 0) {
            // 공짜 지점: 강하면 밸류벳/레이즈(BB 옵션), 아니면 체크.
            // 기준은 인원수 비례 — 멀티웨이에선 절대 이퀴티가 아니라 "공평 지분 대비 우위"로 판단
            double valueBar = BET_SHARE_MULTIPLIER / (opponents + 1) + jitter;
            if (equity > valueBar) {
                if (legal.contains(ActionType.BET)) {
                    return new Decision("BET", clampBet(engine, me, pot * 2 / 3),
                            "이퀴티 %s%% > 밸류벳 기준 %s%% → 팟 2/3 벳".formatted(pct(equity), pct(valueBar)));
                }
                if (legal.contains(ActionType.RAISE)) {
                    return new Decision("RAISE", clampRaise(engine, me, engine.minRaiseTo()),
                            "이퀴티 %s%% > 밸류벳 기준 %s%% → 레이즈(BB 옵션)".formatted(pct(equity), pct(valueBar)));
                }
            }
            return new Decision("CHECK", 0,
                    "공짜로 볼 수 있고 이퀴티 %s%% ≤ 밸류벳 기준 %s%% → 체크".formatted(pct(equity), pct(valueBar)));
        }

        double required = (double) toCall / (pot + toCall);
        if (equity < required - FOLD_MARGIN + jitter) {
            return new Decision("FOLD", 0,
                    "이퀴티 %s%% < 필요이퀴티 %s%%(콜 %d/팟 %d) − 마진 → 폴드"
                            .formatted(pct(equity), pct(required), toCall, pot));
        }
        if (equity > required + RAISE_MARGIN + jitter && legal.contains(ActionType.RAISE)) {
            // 팟 크기만큼 올리기(최소 레이즈 미만이면 최소 레이즈로, 스택 초과면 올인)
            return new Decision("RAISE", clampRaise(engine, me, engine.currentBet() + pot),
                    "이퀴티 %s%% ≫ 필요이퀴티 %s%% + 레이즈마진 %s%%p → 팟 레이즈"
                            .formatted(pct(equity), pct(required), pct(RAISE_MARGIN)));
        }
        return new Decision("CALL", 0,
                "이퀴티 %s%% ≥ 필요이퀴티 %s%%(콜 %d/팟 %d) → 콜"
                        .formatted(pct(equity), pct(required), toCall, pot));
    }

    private static long pct(double x) {
        return Math.round(x * 100);
    }

    /** 벳 금액을 [최소벳, 스택] 으로 자른다. */
    static long clampBet(HandEngine engine, Player me, long desired) {
        long min = engine.minRaiseTo(); // 벳 전이므로 = 빅블라인드
        return Math.min(me.stack(), Math.max(min, desired));
    }

    /** 레이즈-투 금액을 [최소 레이즈-투, 올인 한도] 로 자른다(올인이면 최소 미만도 엔진이 허용). */
    static long clampRaise(HandEngine engine, Player me, long desiredTo) {
        long allInTo = engine.committedThisStreet(me.id()) + me.stack();
        return Math.min(allInTo, Math.max(engine.minRaiseTo(), desiredTo));
    }
}
