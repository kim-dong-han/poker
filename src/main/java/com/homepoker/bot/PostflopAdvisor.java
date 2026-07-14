package com.homepoker.bot;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.eval.HandEvaluator;
import com.homepoker.engine.eval.HandRank;
import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.HandLog;
import com.homepoker.engine.game.Player;
import com.homepoker.engine.game.PlayerStatus;
import com.homepoker.engine.game.Street;
import com.homepoker.stats.PlayerStats;
import com.homepoker.stats.StatsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * 해링턴 캐시게임(6-max) 요약 규칙 기반 포스트플랍 조언자.
 * 핵심 규칙(요약: 로컬 캐시게임_전략요약.md — 저작권상 레포 미포함):
 *  - 핸드 강도 5분류: 괴물(스트레이트+)/아주 강함(셋·투페어)/강함(오버페어·톱페어)/
 *    미디엄(그 외 페어 — 싸게 쇼다운)/드로우/에어
 *  - 플랍 텍스처: 마른/젖은/페어드/A하이 — c-bet 은 마른·페어드·A하이 보드에서(젖은 보드 금지)
 *  - 베팅 사이즈: 마른 보드 1/2팟, 밸류·드로우 보드 2/3, 젖은 보드 팟(드로우 배당 박탈),
 *    멀티웨이는 한 단계 크게
 *  - 팟 컨트롤: 원페어는 큰 팟 금지(턴 감속), 리버 씬 밸류는 1/2팟, 리버 레이즈 자제(콜만)
 *  - 경보: 내 벳에 레이즈 = 진짜 강함 → 원페어 폴드(콤보 드로우는 예외)
 *  - 멀티웨이: 블러프 금지·베팅 기준 상향 ("선수가 많을수록 베팅은 실제 강도에 가깝다")
 *
 * 규칙 밖(분류 불가·특수 상황)이면 empty 를 돌려주고 BotBrain 의 이퀴티 로직이 폴백한다.
 * 몬테카를로 없이 결정적으로 판단하므로(드로우는 아웃 수 근사) 테스트가 재현 가능하다.
 */
@Service
public class PostflopAdvisor {

    /** 핸드 강도 5분류(+에어). 순서 = 강한 것부터. */
    enum HandClass { MONSTER, VERY_STRONG, STRONG, MEDIUM, DRAW, AIR }

    /** 플랍 텍스처 4분류. */
    enum Texture { DRY, WET, PAIRED, ACE_HIGH }

    /** 분류 결과. comboDraw = 페어/드로우 결합(페어+플러쉬드로우 등 — 올인 불사급). */
    record Reading(HandClass handClass, boolean comboDraw, int outs) {}

    /** 스트리트당 아웃 1개의 근사 이퀴티(2.13%). */
    private static final double OUT_EQUITY = 0.0213;
    /** 드로우 콜에 얹어주는 보수적 임플라이드 오즈 보정. */
    private static final double IMPLIED_BONUS = 0.04;

    /** 상대 유형(해링턴 Part 2 게임플랜). null = 표본 부족/보통 → 기본 규칙. */
    enum OppType { NIT, CALLING_STATION, LAG }

    /** 유형 판별에 필요한 최소 핸드 표본. */
    private static final int MIN_SAMPLE = 30;

    private final boolean enabled;
    private final double cbetFreq;
    private final double stealFreq;
    private final double riverValueFreq;
    private final StatsService statsService; // null 허용 — 상대 모델링 없이 동작

    @org.springframework.beans.factory.annotation.Autowired
    public PostflopAdvisor(StatsService statsService,
                           @Value("${poker.bot.cbet-freq:0.7}") double cbetFreq,
                           @Value("${poker.bot.steal-freq:0.4}") double stealFreq,
                           @Value("${poker.bot.river-value-freq:0.8}") double riverValueFreq) {
        this(true, cbetFreq, stealFreq, riverValueFreq, statsService);
    }

    /** 상대 모델링 없이 규칙만 쓰는 조언자(단위테스트용). */
    public PostflopAdvisor(double cbetFreq, double stealFreq, double riverValueFreq) {
        this(true, cbetFreq, stealFreq, riverValueFreq, null);
    }

    private PostflopAdvisor(boolean enabled, double cbetFreq, double stealFreq,
                            double riverValueFreq, StatsService statsService) {
        this.enabled = enabled;
        this.cbetFreq = cbetFreq;
        this.stealFreq = stealFreq;
        this.riverValueFreq = riverValueFreq;
        this.statsService = statsService;
    }

    /** 항상 empty(기존 이퀴티 로직만 검증하는 테스트·비활성 환경용). */
    public static PostflopAdvisor disabled() {
        return new PostflopAdvisor(false, 0.7, 0.4, 0.8, null);
    }

    /**
     * 헤즈업 팟의 단일 상대를 통계로 유형 판별(니트/콜스테이션/LAG).
     * 표본 부족·멀티웨이·통계 없음 → null(기본 규칙).
     */
    private OppType opponentType(HandEngine engine, String botId) {
        if (statsService == null) {
            return null;
        }
        List<Player> opps = engine.players().stream()
                .filter(p -> !p.id().equals(botId) && p.status() != PlayerStatus.FOLDED)
                .toList();
        if (opps.size() != 1) {
            return null;
        }
        PlayerStats s = statsService.statsFor(opps.get(0).id());
        if (s == null || s.handsPlayed() < MIN_SAMPLE) {
            return null;
        }
        if (s.vpip() > 0.35 && s.af() < 1.2 && s.postflopSamples() >= 10) {
            return OppType.CALLING_STATION;
        }
        if (s.vpip() < 0.16) {
            return OppType.NIT;
        }
        if (s.vpip() > 0.25 && s.pfr() > 0.18 && s.af() > 2.5 && s.postflopSamples() >= 10) {
            return OppType.LAG;
        }
        return null;
    }

    public Optional<BotBrain.Decision> advise(HandEngine engine, String botId, Random rng) {
        Street st = engine.street();
        if (!enabled || (st != Street.FLOP && st != Street.TURN && st != Street.RIVER)) {
            return Optional.empty();
        }
        Player me = engine.players().stream()
                .filter(p -> p.id().equals(botId)).findFirst().orElseThrow();
        if (me.holeCards().size() != 2 || engine.board().size() < 3) {
            return Optional.empty();
        }
        Reading rd = classify(me.holeCards(), engine.board());
        Texture tx = texture(engine.board().subList(0, 3));
        Hist hist = replay(engine.log(), botId, engine.street());
        boolean multiway = engine.players().stream()
                .filter(p -> !p.id().equals(botId) && p.status() != PlayerStatus.FOLDED)
                .count() >= 2;
        OppType opp = opponentType(engine, botId);
        long pot = engine.pot();
        long toCall = Math.min(engine.amountToCall(botId), me.stack());
        Set<ActionType> legal = engine.legalActions(botId);

        if (toCall > 0 && BotBrain.facingEffectiveAllIn(engine, me)) {
            // 올인 대치: "큰 벳엔 원페어 폴드" 류 규칙을 100% 적용하면 아무 패 올인에
            // 그대로 착취당한다 → 이퀴티 vs 팟오즈 폴백(무지성 올인일수록 랜덤 가정이 정확)
            return Optional.empty();
        }
        return toCall == 0
                ? adviseNoBet(engine, me, rd, tx, hist, multiway, opp, pot, legal, rng)
                : adviseFacingBet(engine, me, rd, hist, multiway, opp, pot, toCall, legal);
    }

    /* ---------- 맞출 벳이 없을 때(벳/체크) ---------- */

    private Optional<BotBrain.Decision> adviseNoBet(HandEngine engine, Player me, Reading rd,
                                                    Texture tx, Hist hist, boolean multiway,
                                                    OppType opp, long pot, Set<ActionType> legal,
                                                    Random rng) {
        Street st = engine.street();
        switch (rd.handClass()) {
            case MONSTER, VERY_STRONG -> {
                // 큰 핸드 큰 팟: 젖은 보드 팟사이즈(배당 박탈), 마른/페어드는 1/2 로 유도
                int steps = tx == Texture.WET ? 3 : (tx == Texture.ACE_HIGH ? 2 : 1);
                if (multiway) {
                    steps = Math.min(3, steps + 1); // 멀티웨이는 한 단계 크게
                }
                return bet(engine, me, pot, steps, legal,
                        "해링턴: %s — %s 보드 밸류벳(큰 핸드 큰 팟)".formatted(label(rd), texLabel(tx)));
            }
            case STRONG -> {
                if (st == Street.FLOP) {
                    int steps = tx == Texture.WET ? 2 : 1; // 젖은 보드는 반드시 크게
                    return bet(engine, me, pot, steps, legal,
                            "해링턴: %s — 플랍 밸류벳(%s 보드)".formatted(label(rd), texLabel(tx)));
                }
                if (st == Street.TURN) {
                    // 팟 컨트롤: 원페어는 큰 팟 금지 — 레버리지 줄인 1/2팟
                    return bet(engine, me, pot, 1, legal, "해링턴: 원페어 턴 — 팟 컨트롤 1/2팟");
                }
                // 리버: 상대 "라인"부터 본다 — 포스트플랍에 벳이 한 번도 없었다면(모두 체크로
                // 온 팟) 상대 레인지가 스케어 카드·멀티웨이와 무관하게 약함 → 밸류벳이 정답
                // (교재: "겁먹은 체크는 대형 실수. 상대 라인이 그 강함과 일치하는지 보고 밸류벳").
                // 단 100% 고정이면 벳=원페어 확정/체크=노페어 확정으로 읽혀 착취당하므로
                // riverValueFreq 빈도로 섞는다 — 가끔 체크해 체크 범위에도 밸류를 남긴다.
                if (!hist.postflopBetSeen()) {
                    if (rng.nextDouble() < riverValueFreq) {
                        return bet(engine, me, pot, 1, legal,
                                "해링턴: 모두 체크로 온 리버 — 상대 라인이 약함, 원페어 밸류벳 1/2팟(겁먹은 체크는 실수)");
                    }
                    return check(legal, "해링턴: 리버 밸류 스팟이지만 빈도 혼합 — 체크(범위 위장)");
                }
                // 앞 스트리트에 액션이 있었던 팟만 스케어/멀티웨이 경계를 적용한다
                if (scaryRiver(engine.board())) {
                    return check(legal, "해링턴: 리버 스케어 보드(플러쉬/스트레이트 완성) + 앞 스트리트 액션 — 원페어 체크");
                }
                if (multiway) {
                    return check(legal, "해링턴: 멀티웨이 리버 + 앞 스트리트 액션 — 원페어 씬 밸류 생략(체크)");
                }
                if (rng.nextDouble() < riverValueFreq) {
                    return bet(engine, me, pot, 1, legal, "해링턴: 리버 씬 밸류 1/2팟(겁먹은 체크는 실수)");
                }
                return check(legal, "해링턴: 리버 밸류 스팟이지만 빈도 혼합 — 체크(범위 위장)");
            }
            case MEDIUM -> {
                return check(legal, "해링턴: 미디엄 핸드 — 목표는 싸게 쇼다운(체크)");
            }
            case DRAW -> {
                if (st == Street.FLOP && (rd.comboDraw() || !multiway)) {
                    return bet(engine, me, pot, 2, legal,
                            "해링턴: 드로우(%d아웃) 세미블러프 — 폴드 유도 + 완성 아웃".formatted(rd.outs()));
                }
                if (st == Street.TURN && rd.comboDraw()) {
                    return bet(engine, me, pot, 2, legal, "해링턴: 콤보 드로우 턴 — 계속 압박");
                }
                return check(legal, "해링턴: 드로우 — 공짜 카드(턴 배당 악화, 체크)");
            }
            case AIR -> {
                if (opp == OppType.CALLING_STATION || opp == OppType.LAG) {
                    // 콜스테이션 = 안 접음 / LAG = 콜·레이즈로 반격 → 에어 블러프 금지
                    return check(legal, "해링턴: 상대 유형(%s) — 블러프 금지(체크)".formatted(oppLabel(opp)));
                }
                boolean cbetBoard = tx == Texture.DRY || tx == Texture.PAIRED || tx == Texture.ACE_HIGH;
                double freq = opp == OppType.NIT ? 1.0 : cbetFreq; // 니트에겐 항상 C-벳
                if (st == Street.FLOP && hist.preflopAggressor() && !multiway && cbetBoard
                        && rng.nextDouble() < freq) {
                    return bet(engine, me, pot, 1, legal,
                            "해링턴: %s 보드 C-벳(에어) — 1/2팟(2/3 폴드시키면 흑자)".formatted(texLabel(tx)));
                }
                if (st == Street.TURN && hist.flopCheckedThrough() && !multiway
                        && rng.nextDouble() < stealFreq) {
                    return bet(engine, me, pot, 2, legal, "해링턴: 모두 체크한 팟 — 턴 스틸");
                }
                return check(legal, st == Street.FLOP && !cbetBoard
                        ? "해링턴: 젖은 보드 — C-벳 금지(체크)"
                        : "해링턴: 에어 — 포기(체크)");
            }
        }
        return Optional.empty();
    }

    /* ---------- 벳을 마주했을 때(레이즈/콜/폴드) ---------- */

    private Optional<BotBrain.Decision> adviseFacingBet(HandEngine engine, Player me, Reading rd,
                                                        Hist hist, boolean multiway, OppType opp,
                                                        long pot, long toCall, Set<ActionType> legal) {
        Street st = engine.street();
        double required = (double) toCall / (pot + toCall);
        long potBefore = Math.max(1, pot - toCall); // 상대 벳이 들어가기 전 팟(사이즈 비교 기준)
        boolean alarm = hist.raisedOverMyBet(); // 내 벳에 레이즈 = 진짜 강함

        switch (rd.handClass()) {
            case MONSTER -> {
                return raiseOrCall(engine, me, engine.currentBet() + pot, legal,
                        "해링턴: 괴물 핸드 — 팟 레이즈(최대 가치 추출)");
            }
            case VERY_STRONG -> {
                if (st == Street.RIVER && toCall * 3 > potBefore) {
                    return call("해링턴: 아주 강함 — 리버 레이즈 자제(콜만)");
                }
                if (opp == OppType.LAG && st == Street.FLOP) {
                    return call("해링턴: LAG 상대 트랩 — 콜로 동행(그의 베팅 유도)");
                }
                return raiseOrCall(engine, me, engine.currentBet() + pot, legal,
                        "해링턴: 셋/투페어 — 레이즈로 팟 키움");
            }
            case STRONG -> {
                if (alarm && !rd.comboDraw()) {
                    if (opp == OppType.LAG) {
                        return call("해링턴: LAG 의 레이즈는 역해석 — 원페어 콜다운");
                    }
                    return fold("해링턴: 내 벳에 레이즈 = 경보 → 원페어 폴드");
                }
                if (opp == OppType.NIT && st != Street.FLOP && toCall * 3 > potBefore) {
                    return fold("해링턴: 니트의 턴/리버 베팅 = 진짜 강함 → 원페어 폴드");
                }
                boolean bigBet = toCall * 3 > potBefore * 2; // 2/3팟 초과
                if (st == Street.RIVER && bigBet) {
                    return fold("해링턴: 리버 큰 벳 — \"상대가 강한 핸드로 이렇게 플레이했나\" → 원페어 폴드");
                }
                if (multiway && bigBet && st != Street.FLOP) {
                    return fold("해링턴: 멀티웨이 큰 벳 = 실제 강함 → 원페어 폴드");
                }
                return call("해링턴: 톱페어/오버페어 — 콜(레이즈 없인 팟 안 키움)");
            }
            case MEDIUM -> {
                if (!alarm && toCall * 3 <= potBefore) { // 1/3팟 이하만 배당으로 콜
                    return call("해링턴: 미디엄 — 작은 벳(≤1/3팟)은 배당 콜, 싸게 쇼다운");
                }
                return fold("해링턴: 미디엄 — 큰 벳엔 폴드(팟 커지면 지는 핸드)");
            }
            case DRAW -> {
                if (rd.comboDraw() && st == Street.FLOP && !multiway
                        && legal.contains(ActionType.RAISE)) {
                    return raiseOrCall(engine, me, engine.currentBet() + pot, legal,
                            "해링턴: 콤보 드로우(%d아웃) — 세미블러프 레이즈(vs 톱페어도 코인플립)"
                                    .formatted(rd.outs()));
                }
                // 플랍은 남은 두 장 기준(룰 오브 4 — 드로우는 플랍이 가장 배당 좋음), 턴은 한 장
                double perOut = st == Street.FLOP ? 0.04 : OUT_EQUITY;
                double eq = rd.outs() * perOut + (multiway ? 0 : IMPLIED_BONUS);
                if (eq >= required) {
                    return call("해링턴: 드로우 %d아웃 ≈%d%% ≥ 필요 %d%% → 배당 콜"
                            .formatted(rd.outs(), Math.round(eq * 100), Math.round(required * 100)));
                }
                return fold("해링턴: 드로우 %d아웃 ≈%d%% < 필요 %d%% → 폴드"
                        .formatted(rd.outs(), Math.round(eq * 100), Math.round(required * 100)));
            }
            case AIR -> {
                return fold("해링턴: 에어 — 쇼다운 가치 없음, 폴드");
            }
        }
        return Optional.empty();
    }

    /* ---------- 핸드 강도 분류 ---------- */

    static Reading classify(List<Card> hole, List<Card> board) {
        List<Card> all = new ArrayList<>(hole);
        all.addAll(board);
        HandRank rank = HandEvaluator.evaluate(all);
        List<Integer> boardVals = board.stream().map(c -> c.rank().value()).toList();
        int maxBoard = boardVals.stream().max(Integer::compare).orElse(0);
        int h1 = hole.get(0).rank().value();
        int h2 = hole.get(1).rank().value();
        boolean pocketPair = h1 == h2;

        HandClass cls;
        switch (rank.category()) {
            case STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_A_KIND, STRAIGHT_FLUSH -> cls = HandClass.MONSTER;
            case THREE_OF_A_KIND -> {
                int trips = rank.tiebreakers().get(0);
                long onBoard = boardVals.stream().filter(v -> v == trips).count();
                // 보드 자체 트립스(내 홀카드 무관)면 키커 싸움 = 미디엄, 셋/트립스는 아주 강함
                cls = onBoard >= 3 ? HandClass.MEDIUM : HandClass.VERY_STRONG;
            }
            case TWO_PAIR -> {
                int p1 = rank.tiebreakers().get(0);
                int p2 = rank.tiebreakers().get(1);
                boolean h1In = h1 == p1 || h1 == p2;
                boolean h2In = h2 == p1 || h2 == p2;
                if (h1In && h2In && !pocketPair) {
                    cls = HandClass.VERY_STRONG;        // 홀카드 두 장 다 맞은 진짜 투페어
                } else if (pocketPair && (h1 == p1 || h1 == p2)) {
                    // 포켓페어 + 보드 페어: 실질은 (오버)페어 취급
                    cls = h1 >= maxBoard ? HandClass.STRONG : HandClass.MEDIUM;
                } else if (h1In || h2In) {
                    int myPair = h1In ? h1 : h2;        // 보드 페어 + 내 원페어
                    cls = myPair >= maxBoard ? HandClass.STRONG : HandClass.MEDIUM;
                } else {
                    cls = HandClass.MEDIUM;             // 보드 더블 페어 — 키커 쇼다운
                }
            }
            case ONE_PAIR -> {
                int pair = rank.tiebreakers().get(0);
                long pairOnBoard = boardVals.stream().filter(v -> v == pair).count();
                if (pairOnBoard >= 2) {
                    cls = HandClass.AIR;                // 페어가 보드에만 있음 = 노페어
                } else if (pocketPair) {
                    cls = h1 > maxBoard ? HandClass.STRONG : HandClass.MEDIUM; // 오버페어/언더페어
                } else {
                    cls = pair == maxBoard ? HandClass.STRONG : HandClass.MEDIUM; // 톱페어/그 외
                }
            }
            default -> cls = HandClass.AIR;
        }

        // 드로우(플랍·턴만): 플러쉬 드로우/양차 스트레이트 드로우. 콤보 = 페어나 양쪽 드로우 결합
        boolean comboDraw = false;
        int outs = 0;
        if (board.size() < 5) {
            boolean fd = flushDraw(hole, board);
            boolean oesd = openEnded(hole, board);
            boolean pairedHand = cls == HandClass.STRONG || cls == HandClass.MEDIUM;
            if (fd || oesd) {
                outs = fd && oesd ? 15 : (fd ? 9 : 8); // FD 9 / OESD 8 / 겹침 보정 15
                comboDraw = (fd && oesd) || (fd && pairedHand);
                if (cls == HandClass.MEDIUM || cls == HandClass.AIR) {
                    cls = HandClass.DRAW;
                }
            }
        }
        return new Reading(cls, comboDraw, outs);
    }

    /** 플러쉬 드로우: 홀카드 포함 4장 동일 무늬. */
    static boolean flushDraw(List<Card> hole, List<Card> board) {
        for (var suit : com.homepoker.engine.card.Suit.values()) {
            long total = 0;
            long mine = 0;
            for (Card c : hole) {
                if (c.suit() == suit) {
                    total++;
                    mine++;
                }
            }
            for (Card c : board) {
                if (c.suit() == suit) {
                    total++;
                }
            }
            if (total == 4 && mine >= 1) {
                return true;
            }
        }
        return false;
    }

    /** 양차 스트레이트 드로우: 홀카드 포함 연속 4장 + 양끝이 다 열려 있음. */
    static boolean openEnded(List<Card> hole, List<Card> board) {
        Set<Integer> vals = new TreeSet<>();
        Set<Integer> holeVals = new HashSet<>();
        for (Card c : hole) {
            vals.add(c.rank().value());
            holeVals.add(c.rank().value());
        }
        for (Card c : board) {
            vals.add(c.rank().value());
        }
        for (int low = 2; low <= 10; low++) { // low..low+3, 양끝(low-1, low+4) 개선 가능해야 양차
            boolean run = true;
            boolean usesHole = false;
            for (int v = low; v < low + 4; v++) {
                if (!vals.contains(v)) {
                    run = false;
                    break;
                }
                if (holeVals.contains(v)) {
                    usesHole = true;
                }
            }
            if (run && usesHole && low >= 2 && low + 4 <= 14
                    && !vals.contains(low + 4) && !vals.contains(low - 1)) {
                return true;
            }
        }
        return false;
    }

    /* ---------- 플랍 텍스처 ---------- */

    static Texture texture(List<Card> flop) {
        List<Integer> vals = flop.stream().map(c -> c.rank().value()).sorted().toList();
        long distinctRanks = vals.stream().distinct().count();
        long suits = flop.stream().map(Card::suit).distinct().count();
        if (distinctRanks < 3) {
            return Texture.PAIRED;
        }
        boolean connected = vals.get(2) - vals.get(0) <= 4 && vals.get(1) >= 5;
        boolean twoTone = suits <= 2;
        if (suits == 1 || (twoTone && connected)) {
            return Texture.WET;
        }
        if (vals.get(2) == 14) {
            return Texture.ACE_HIGH;
        }
        return connected ? Texture.WET : Texture.DRY;
    }

    /** 리버 스케어 보드: 보드 3장 이상 동일 무늬(플러쉬 완성권) 또는 4연속. */
    static boolean scaryRiver(List<Card> board) {
        for (var suit : com.homepoker.engine.card.Suit.values()) {
            if (board.stream().filter(c -> c.suit() == suit).count() >= 3) {
                return true;
            }
        }
        Set<Integer> vals = new TreeSet<>();
        for (Card c : board) {
            vals.add(c.rank().value());
            if (c.rank().value() == 14) {
                vals.add(1);
            }
        }
        int streak = 0;
        int prev = -9;
        for (int v : vals) {
            streak = v == prev + 1 ? streak + 1 : 1;
            prev = v;
            if (streak >= 4) {
                return true;
            }
        }
        return false;
    }

    /* ---------- 핸드 히스토리(이벤트 소싱 재생) ---------- */

    /**
     * 프리플랍 어그레서 여부·이번 스트리트에서 내 벳이 레이즈 맞았는지·플랍 체크 통과 여부·
     * 포스트플랍에 벳/레이즈가 한 번이라도 있었는지(상대 라인 판독의 원료).
     */
    record Hist(boolean preflopAggressor, boolean raisedOverMyBet, boolean flopCheckedThrough,
                boolean postflopBetSeen) {}

    static Hist replay(HandLog log, String botId, Street current) {
        HandEngine e = log.stateAt(0);
        String lastPreflopRaiser = null;
        boolean myBetThisStreet = false;
        boolean raisedOverMe = false;
        boolean flopHadBet = false;
        boolean sawFlop = false;
        boolean postflopBet = false;
        Street prev = e.street();
        for (Action a : log.actions()) {
            Street st = e.street();
            if (st != prev) { // 스트리트 전환 — 현재 스트리트 추적 초기화
                myBetThisStreet = false;
                raisedOverMe = false;
                prev = st;
            }
            if (st == Street.PREFLOP && a.type() == ActionType.RAISE) {
                lastPreflopRaiser = a.playerId();
            }
            if (st != Street.PREFLOP && (a.type() == ActionType.BET || a.type() == ActionType.RAISE)) {
                postflopBet = true;
            }
            if (st == Street.FLOP) {
                sawFlop = true;
                if (a.type() == ActionType.BET || a.type() == ActionType.RAISE) {
                    flopHadBet = true;
                }
            }
            if (st == current) {
                if (a.playerId().equals(botId)
                        && (a.type() == ActionType.BET || a.type() == ActionType.RAISE)) {
                    myBetThisStreet = true;
                } else if (myBetThisStreet && a.type() == ActionType.RAISE) {
                    raisedOverMe = true;
                }
            }
            e.apply(a);
        }
        return new Hist(botId.equals(lastPreflopRaiser), raisedOverMe, sawFlop && !flopHadBet,
                postflopBet);
    }

    /* ---------- 헬퍼 ---------- */

    /** steps: 1=1/2팟, 2=2/3팟, 3=팟. BET 불가면 체크로 후퇴. */
    private static Optional<BotBrain.Decision> bet(HandEngine engine, Player me, long pot,
                                                   int steps, Set<ActionType> legal, String reason) {
        long desired = switch (steps) {
            case 1 -> pot / 2;
            case 2 -> pot * 2 / 3;
            default -> pot;
        };
        if (legal.contains(ActionType.BET)) {
            return Optional.of(new BotBrain.Decision("BET",
                    BotBrain.clampBet(engine, me, desired), reason));
        }
        if (legal.contains(ActionType.RAISE)) { // 이번 스트리트에 이미 벳이 있는 희귀 케이스
            return Optional.of(new BotBrain.Decision("RAISE",
                    BotBrain.clampRaise(engine, me, engine.currentBet() + desired), reason));
        }
        return check(legal, reason + " — 벳 불가, 체크");
    }

    private static Optional<BotBrain.Decision> check(Set<ActionType> legal, String reason) {
        if (legal.contains(ActionType.CHECK)) {
            return Optional.of(new BotBrain.Decision("CHECK", 0, reason));
        }
        return Optional.empty(); // 체크 불가면 규칙 밖 — 이퀴티 폴백
    }

    private static Optional<BotBrain.Decision> raiseOrCall(HandEngine engine, Player me,
                                                           long desiredTo, Set<ActionType> legal,
                                                           String reason) {
        if (legal.contains(ActionType.RAISE)) {
            return Optional.of(new BotBrain.Decision("RAISE",
                    BotBrain.clampRaise(engine, me, desiredTo), reason));
        }
        return call(reason + " — 레이즈 불가(상대 올인), 콜");
    }

    private static Optional<BotBrain.Decision> call(String reason) {
        return Optional.of(new BotBrain.Decision("CALL", 0, reason));
    }

    private static Optional<BotBrain.Decision> fold(String reason) {
        return Optional.of(new BotBrain.Decision("FOLD", 0, reason));
    }

    private static String label(Reading rd) {
        return switch (rd.handClass()) {
            case MONSTER -> "괴물(스트레이트+)";
            case VERY_STRONG -> "아주 강함(셋/투페어)";
            case STRONG -> "강함(오버페어/톱페어)";
            case MEDIUM -> "미디엄";
            case DRAW -> "드로우";
            case AIR -> "에어";
        };
    }

    private static String oppLabel(OppType opp) {
        return switch (opp) {
            case NIT -> "니트";
            case CALLING_STATION -> "콜스테이션";
            case LAG -> "LAG";
        };
    }

    private static String texLabel(Texture tx) {
        return switch (tx) {
            case DRY -> "마른";
            case WET -> "젖은";
            case PAIRED -> "페어드";
            case ACE_HIGH -> "A하이";
        };
    }
}
