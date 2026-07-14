package com.homepoker.bot;

import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.Player;
import com.homepoker.engine.game.PlayerStatus;
import com.homepoker.engine.game.Street;
import com.homepoker.range.BtsPreflopCharts;
import com.homepoker.range.HandNotation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * 차트 기반 프리플랍 조언자. 프리플랍 상황(오픈/3벳/BB방어/4벳/5벳/스퀴즈)을 액션 로그로
 * 판별하고, 전사된 차트(BtsPreflopCharts)에서 핸드의 액션 빈도를 찾아 확률적으로 결정한다.
 *
 * 차트가 없거나(파일 미보유) 매치업 차트가 없는 상황이면 empty 를 돌려주고,
 * BotBrain 이 기존 "이퀴티 vs 팟오즈" 로직으로 폴백한다 — 차트는 순수 애드온이다.
 *
 * 경계선(노랑) 핸드: 책에선 "타이트/위크 상대 한정" 조건부지만, 봇은 상대 모델이 없으므로
 * 고정 빈도(poker.bot.borderline-freq, 기본 0.35)로 섞는다 — 혼합 전략의 예측 불가성도 겸한다.
 *
 * 헤즈업 오픈 보정: 차트는 6-max 용이라 SB 오픈이 콤보 기준 약 49%에 그치는데, 헤즈업 버튼의
 * 정석 오픈은 75~85%(블라인드가 절반씩 걸려 레인지가 크게 넓어짐). 그래서 2인 테이블의 첫 오픈은
 * 경계선을 전량 오픈으로 승격하고, 차트 밖 핸드도 poker.bot.hu-open-boost(기본 0.5) 빈도로
 * 섞어 연다 — 특정 핸드 "무조건 오픈"이 아니라 빈도 보정이므로 착취당하지 않는다.
 */
@Service
public class PreflopAdvisor {

    private final BtsPreflopCharts charts;
    private final double borderlineFreq;
    private final double huOpenBoost;

    @org.springframework.beans.factory.annotation.Autowired
    public PreflopAdvisor(BtsPreflopCharts charts,
                          @Value("${poker.bot.borderline-freq:0.35}") double borderlineFreq,
                          @Value("${poker.bot.hu-open-boost:0.5}") double huOpenBoost) {
        this.charts = charts;
        this.borderlineFreq = borderlineFreq;
        this.huOpenBoost = huOpenBoost;
    }

    /** 헤즈업 보정 기본값(0.5)을 쓰는 편의 생성자(기존 테스트 호환). */
    public PreflopAdvisor(BtsPreflopCharts charts, double borderlineFreq) {
        this(charts, borderlineFreq, 0.5);
    }

    /** 차트 없이 동작(항상 empty) — 이퀴티 로직만 검증하는 테스트용. */
    public static PreflopAdvisor disabled() {
        return new PreflopAdvisor(BtsPreflopCharts.empty(), 0.35);
    }

    public Optional<BotBrain.Decision> advise(HandEngine engine, String botId, Random rng) {
        if (!charts.available() || engine.street() != Street.PREFLOP) {
            return Optional.empty();
        }
        List<Player> players = engine.players();
        Map<String, String> pos = positions(players, engine.buttonSeat());
        String myPos = pos.get(botId);
        if (myPos == null) {
            return Optional.empty();
        }
        Player me = players.stream().filter(p -> p.id().equals(botId)).findFirst().orElseThrow();
        if (me.holeCards().size() != 2) {
            return Optional.empty();
        }
        String hand = HandNotation.of(me.holeCards());
        long bb = engine.log().bigBlind();

        // 현재 스트리트가 프리플랍이므로 로그의 모든 액션이 프리플랍 액션이다
        List<Action> acts = engine.log().actions();
        List<Action> raises = acts.stream().filter(a -> a.type() == ActionType.RAISE).toList();
        Set<ActionType> legal = engine.legalActions(botId);

        return switch (raises.size()) {
            case 0 -> adviseOpen(engine, me, myPos, hand, bb, acts, legal, rng);
            case 1 -> adviseVsOpen(engine, me, myPos, hand, bb, pos, acts, raises, legal, rng);
            case 2 -> adviseVs3Bet(engine, me, myPos, hand, pos, raises, legal, rng);
            case 3 -> adviseVs4Bet(engine, me, myPos, hand, pos, raises, legal, rng);
            default -> Optional.empty(); // 5벳 이상의 워: 차트 밖 → 이퀴티 폴백
        };
    }

    /* ---------- 상황별 판단 ---------- */

    /** 아무도 레이즈하지 않음: RFI(첫 오픈). 림프가 있으면 사이즈만 키운다. BB 는 차트 밖(폴백). */
    private Optional<BotBrain.Decision> adviseOpen(HandEngine engine, Player me, String myPos,
                                                   String hand, long bb, List<Action> acts,
                                                   Set<ActionType> legal, Random rng) {
        if ("BB".equals(myPos) || !charts.hasChart("openRaise", myPos)) {
            return Optional.empty(); // BB 무료 체크/림프 팟은 이퀴티 로직이 처리
        }
        Map<String, Double> a = charts.actions("openRaise", myPos, hand);
        boolean headsUp = engine.players().size() == 2;
        double p;
        if (headsUp) {
            // 헤즈업 버튼: 경계선 전량 오픈 + 차트 밖 핸드도 hu-open-boost 빈도로 오픈(클래스 주석 참조)
            double chartP = Math.min(1.0, a.getOrDefault("open", 0.0) + a.getOrDefault("openBorderline", 0.0));
            p = chartP + huOpenBoost * (1 - chartP);
        } else {
            p = a.getOrDefault("open", 0.0) + borderlineFreq * a.getOrDefault("openBorderline", 0.0);
        }
        long limpers = acts.stream().filter(x -> x.type() == ActionType.CALL).count();
        if (rng.nextDouble() < p && legal.contains(ActionType.RAISE)) {
            double sizeBb = limpers > 0
                    ? (isIp(myPos) ? 3.5 : 4.5) + limpers   // 림프 상대 공식(책 p3)
                    : charts.openRaiseBB(myPos);
            long to = BotBrain.clampRaise(engine, me, Math.round(sizeBb * bb));
            String basis = headsUp
                    ? "헤즈업 보정 빈도 %d%%".formatted(Math.round(p * 100))
                    : pctText(a, "open", "openBorderline");
            return decision("RAISE", to,
                    "차트: %s 오픈 레인지(%s %s) → %.1fbb 레이즈".formatted(myPos, hand, basis, sizeBb));
        }
        if (legal.contains(ActionType.CHECK)) {
            return decision("CHECK", 0, "차트: %s 오픈 레인지 밖(%s) → 체크".formatted(myPos, hand));
        }
        return decision("FOLD", 0, headsUp
                ? "차트: 헤즈업 보정 빈도 %d%% 미달(%s) → 폴드".formatted(Math.round(p * 100), hand)
                : "차트: %s 오픈 레인지 밖(%s) → 폴드".formatted(myPos, hand));
    }

    /** 오픈 레이즈 하나를 마주함: 3벳 차트 / BB 방어 차트 / (콜러가 있으면) 스퀴즈 차트. */
    private Optional<BotBrain.Decision> adviseVsOpen(HandEngine engine, Player me, String myPos,
                                                     String hand, long bb, Map<String, String> pos,
                                                     List<Action> acts, List<Action> raises,
                                                     Set<ActionType> legal, Random rng) {
        String openerPos = pos.get(raises.get(0).playerId());
        if (openerPos == null || openerPos.equals(myPos)) {
            return Optional.empty();
        }
        int lastRaiseIdx = acts.lastIndexOf(raises.get(0));
        long callers = acts.subList(lastRaiseIdx + 1, acts.size()).stream()
                .filter(x -> x.type() == ActionType.CALL).count();

        if (callers > 0) { // 오픈 + 콜러: 스퀴즈 스팟
            return adviseSqueeze(engine, me, myPos, hand, bb, openerPos, legal, rng);
        }
        if ("BB".equals(myPos)) { // BB 방어: 3벳/콜/폴드 3지선다
            String key = "BB_vs_" + epMerge(openerPos);
            if (!charts.hasChart("bbDefense", key)) {
                return Optional.empty();
            }
            Map<String, Double> a = charts.actions("bbDefense", key, hand);
            double p3 = a.getOrDefault("threeBet", 0.0) + borderlineFreq * a.getOrDefault("threeBetBorderline", 0.0);
            double pc = a.getOrDefault("call", 0.0) + borderlineFreq * a.getOrDefault("callLoose", 0.0);
            double r = rng.nextDouble();
            if (r < p3 && legal.contains(ActionType.RAISE)) {
                long to = BotBrain.clampRaise(engine, me,
                        Math.round(charts.threeBetToBB(openerPos, "BB") * bb));
                return decision("RAISE", to, "차트: BB 방어 vs %s — %s 3벳 구간 → 3벳".formatted(openerPos, hand));
            }
            if (r < p3 + pc && legal.contains(ActionType.CALL)) {
                return decision("CALL", 0, "차트: BB 방어 vs %s — %s 콜 구간 → 콜".formatted(openerPos, hand));
            }
            return decision("FOLD", 0, "차트: BB 방어 vs %s — %s 레인지 밖 → 폴드".formatted(openerPos, hand));
        }
        // 그 외 포지션: 3벳 아니면 폴드(책의 3벳/폴드 전략)
        String key = myPos + "_vs_" + (Set.of("SB").contains(myPos) ? epMerge(openerPos) : openerPos);
        if (!charts.hasChart("threeBet", key)) {
            return Optional.empty();
        }
        Map<String, Double> a = charts.actions("threeBet", key, hand);
        double p = a.getOrDefault("threeBet", 0.0) + borderlineFreq * a.getOrDefault("threeBetBorderline", 0.0);
        if (rng.nextDouble() < p && legal.contains(ActionType.RAISE)) {
            long to = BotBrain.clampRaise(engine, me,
                    Math.round(charts.threeBetToBB(openerPos, myPos) * bb));
            return decision("RAISE", to, "차트: %s 3벳 레인지 vs %s(%s) → 3벳".formatted(myPos, openerPos, hand));
        }
        return decision("FOLD", 0, "차트: %s vs %s — %s 3벳 레인지 밖 → 폴드(3벳/폴드 전략)".formatted(myPos, openerPos, hand));
    }

    /** 내가 오픈했는데 3벳을 맞음: 4벳/콜/폴드 차트. (콜드 3벳 스팟이면 폴백) */
    private Optional<BotBrain.Decision> adviseVs3Bet(HandEngine engine, Player me, String myPos,
                                                     String hand, Map<String, String> pos,
                                                     List<Action> raises, Set<ActionType> legal, Random rng) {
        if (!raises.get(0).playerId().equals(me.id())) {
            return Optional.empty(); // 내가 오프너가 아닌 콜드 스팟 — 차트 밖
        }
        String threeBetterPos = pos.get(raises.get(1).playerId());
        boolean inBlinds = "SB".equals(threeBetterPos) || "BB".equals(threeBetterPos);
        boolean sbVsBb = "SB".equals(myPos) && "BB".equals(threeBetterPos);
        String key = sbVsBb ? "SB_vs_BB3bet" : myPos + (inBlinds ? "_vs_Blinds3bet" : "_vs_IP3bet");
        if (!charts.hasChart("fourBet", key)) {
            return Optional.empty();
        }
        Map<String, Double> a = charts.actions("fourBet", key, hand);
        double p4 = a.getOrDefault("fourBet", 0.0);
        double pc = a.getOrDefault("callVs3Bet", 0.0);
        double r = rng.nextDouble();
        long bb = engine.log().bigBlind();
        if (r < p4 && legal.contains(ActionType.RAISE)) {
            long to = BotBrain.clampRaise(engine, me,
                    Math.round(charts.fourBetToBB(inBlinds, sbVsBb) * bb));
            return decision("RAISE", to, "차트: %s 4벳 레인지(%s) → 4벳".formatted(myPos, hand));
        }
        if (r < p4 + pc && legal.contains(ActionType.CALL)) {
            return decision("CALL", 0, "차트: %s 콜 vs 3벳(%s) → 콜".formatted(myPos, hand));
        }
        return decision("FOLD", 0, "차트: %s — %s 3벳 상대 폴드 구간 → 폴드".formatted(myPos, hand));
    }

    /** 내가 3벳했는데 4벳을 맞음: 5벳(=100bb 올인)/콜/폴드 차트. */
    private Optional<BotBrain.Decision> adviseVs4Bet(HandEngine engine, Player me, String myPos,
                                                     String hand, Map<String, String> pos,
                                                     List<Action> raises, Set<ActionType> legal, Random rng) {
        if (!raises.get(1).playerId().equals(me.id())) {
            return Optional.empty();
        }
        String openerPos = pos.get(raises.get(0).playerId());
        String openerLabel = Set.of("SB", "BB").contains(myPos) ? epMerge(openerPos) : openerPos;
        String key = myPos + "3bet_vs_" + openerLabel + "4bet";
        if (!charts.hasChart("fiveBet", key)) {
            return Optional.empty();
        }
        Map<String, Double> a = charts.actions("fiveBet", key, hand);
        double p5 = a.getOrDefault("fiveBet", 0.0);
        double pc = a.getOrDefault("callVs4Bet", 0.0);
        double r = rng.nextDouble();
        if (r < p5) {
            if (legal.contains(ActionType.RAISE)) {
                long allIn = engine.committedThisStreet(me.id()) + me.stack();
                return decision("RAISE", BotBrain.clampRaise(engine, me, allIn),
                        "차트: %s 5벳 레인지(%s) → 올인".formatted(myPos, hand));
            }
            if (legal.contains(ActionType.CALL)) { // 상대가 이미 올인이면 콜로 강행
                return decision("CALL", 0, "차트: %s 5벳 레인지(%s) — 상대 올인 → 콜".formatted(myPos, hand));
            }
        }
        if (r < p5 + pc && legal.contains(ActionType.CALL)) {
            return decision("CALL", 0, "차트: %s 콜 vs 4벳(%s) → 콜".formatted(myPos, hand));
        }
        return decision("FOLD", 0, "차트: %s — %s 4벳 상대 폴드 구간 → 폴드".formatted(myPos, hand));
    }

    /** 오픈 + 콜러(들) 뒤: 스퀴즈 차트. 사이즈는 IP 팟-1bb / OOP 팟+1bb 근사. */
    private Optional<BotBrain.Decision> adviseSqueeze(HandEngine engine, Player me, String myPos,
                                                      String hand, long bb, String openerPos,
                                                      Set<ActionType> legal, Random rng) {
        if (!charts.hasChart("squeeze", "vs_open_plus_callers")) {
            return Optional.empty();
        }
        Map<String, Double> a = charts.actions("squeeze", "vs_open_plus_callers", hand);
        boolean early = "UTG".equals(openerPos) || "MP".equals(openerPos);
        double p = early
                ? a.getOrDefault("squeezeVsEarly", 0.0)
                        + borderlineFreq * a.getOrDefault("squeezeVsLateOrAdjustEarly", 0.0)
                : a.getOrDefault("squeezeVsLateOrAdjustEarly", 0.0)
                        + borderlineFreq * a.getOrDefault("adjustVsLate", 0.0);
        if (rng.nextDouble() < p && legal.contains(ActionType.RAISE)) {
            long potTarget = engine.currentBet() + engine.pot() + (isIp(myPos) ? -bb : bb);
            long to = BotBrain.clampRaise(engine, me, potTarget);
            return decision("RAISE", to, "차트: 스퀴즈 레인지 vs %s 오픈+콜러(%s) → 스퀴즈".formatted(openerPos, hand));
        }
        return decision("FOLD", 0, "차트: 스퀴즈 레인지 밖(%s) → 폴드".formatted(hand));
    }

    /* ---------- 헬퍼 ---------- */

    /**
     * 좌석 → 책 포지션(UTG/MP/CO/BTN/SB/BB). 6인 미만이면 비는 앞 포지션부터 제거
     * (예: 4인 = CO,BTN,SB,BB). 헤즈업은 버튼이 SB.
     * 7~9인은 늘어난 얼리 좌석을 전부 "UTG"(가장 타이트한 레인지)로 취급해 차트를 계속 쓴다 —
     * 차트가 꺼져 이퀴티 폴백(림프-체크 성향)으로 떨어지는 것보다 훨씬 자연스럽다.
     */
    static Map<String, String> positions(List<Player> players, int buttonSeat) {
        int n = players.size();
        Map<String, String> out = new HashMap<>();
        if (n < 2 || n > 9) {
            return out;
        }
        if (n == 2) {
            out.put(players.get(buttonSeat).id(), "SB");
            out.put(players.get((buttonSeat + 1) % 2).id(), "BB");
            return out;
        }
        out.put(players.get((buttonSeat + 1) % n).id(), "SB");
        out.put(players.get((buttonSeat + 2) % n).id(), "BB");
        String[] nonBlind = {"UTG", "MP", "CO", "BTN"};
        int m = n - 2;
        for (int i = 0; i < m; i++) {
            out.put(players.get((buttonSeat + 3 + i) % n).id(),
                    nonBlind[Math.max(0, 4 - m + i)]);
        }
        return out;
    }

    /** UTG/MP 를 통합 키 "EP" 로(SB·BB 차트가 얼리를 묶어서 제공). */
    private static String epMerge(String pos) {
        return ("UTG".equals(pos) || "MP".equals(pos)) ? "EP" : pos;
    }

    private static boolean isIp(String pos) {
        return "CO".equals(pos) || "BTN".equals(pos);
    }

    private static String pctText(Map<String, Double> a, String main, String borderline) {
        long m = Math.round(a.getOrDefault(main, 0.0) * 100);
        long b = Math.round(a.getOrDefault(borderline, 0.0) * 100);
        return b > 0 ? m + "%+경계선 " + b + "%" : m + "%";
    }

    private static Optional<BotBrain.Decision> decision(String type, long amount, String reason) {
        return Optional.of(new BotBrain.Decision(type, amount, reason));
    }
}
