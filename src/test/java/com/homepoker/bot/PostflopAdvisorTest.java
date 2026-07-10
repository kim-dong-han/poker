package com.homepoker.bot;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.card.Deck;
import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.Player;
import com.homepoker.engine.game.Street;
import com.homepoker.equity.EquityService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 해링턴 규칙 포스트플랍 판단 검증. 혼합 빈도(c-bet/스틸)는 1.0 으로 고정해 결정적으로 만든다.
 */
class PostflopAdvisorTest {

    private static final PostflopAdvisor advisor = new PostflopAdvisor(1.0, 1.0, 1.0);

    private static List<Card> cards(String... notations) {
        List<Card> list = new ArrayList<>();
        for (String s : notations) {
            list.add(Card.of(s));
        }
        return list;
    }

    /**
     * 헤즈업: bot = 버튼/SB(index 0, 포스트플랍 마지막 행동), me = BB.
     * 덱 순서 = bot c1, me c1, bot c2, me c2, 플랍 3장, 턴, 리버.
     * 프리플랍: bot 3bb 오픈, me 콜 → 팟 120, 플랍 진입(bot = 어그레서).
     */
    private static HandEngine flop(String botC1, String botC2, String meC1, String meC2,
                                   String f1, String f2, String f3) {
        Player bot = new Player("bot", "Bot", 1000);
        Player me = new Player("me", "Me", 1000);
        Deck deck = Deck.ofOrder(cards(botC1, meC1, botC2, meC2, f1, f2, f3, "4d", "3c"));
        HandEngine e = new HandEngine(List.of(bot, me), 0, 10, 20, deck);
        e.start();
        e.apply(Action.raiseTo("bot", 60));
        e.apply(Action.call("me"));
        return e; // FLOP, 팟 120, me 먼저 행동
    }

    /* ---------- 분류 단위 테스트 ---------- */

    @Test
    void classifiesHandStrength() {
        // 괴물: 스트레이트
        assertEquals(PostflopAdvisor.HandClass.MONSTER,
                PostflopAdvisor.classify(cards("8s", "9d"), cards("7c", "6h", "Th")).handClass());
        // 아주 강함: 셋
        assertEquals(PostflopAdvisor.HandClass.VERY_STRONG,
                PostflopAdvisor.classify(cards("5s", "5d"), cards("Qc", "5h", "2h")).handClass());
        // 강함: 톱페어 / 오버페어
        assertEquals(PostflopAdvisor.HandClass.STRONG,
                PostflopAdvisor.classify(cards("As", "Qd"), cards("Qc", "6h", "2h")).handClass());
        assertEquals(PostflopAdvisor.HandClass.STRONG,
                PostflopAdvisor.classify(cards("Js", "Jd"), cards("9c", "6h", "2h")).handClass());
        // 미디엄: 언더페어·미들페어, 보드 트립스 키커
        assertEquals(PostflopAdvisor.HandClass.MEDIUM,
                PostflopAdvisor.classify(cards("7s", "7d"), cards("Qc", "6h", "2h")).handClass());
        assertEquals(PostflopAdvisor.HandClass.MEDIUM,
                PostflopAdvisor.classify(cards("As", "2d"), cards("Qc", "Qh", "Qs")).handClass());
        // 에어: 보드 페어만 있는 노페어, 하이카드
        assertEquals(PostflopAdvisor.HandClass.AIR,
                PostflopAdvisor.classify(cards("As", "3d"), cards("Kc", "Kh", "7s")).handClass());
        // 드로우: 플러쉬 드로우(9아웃), 콤보(FD+OESD 15아웃)
        PostflopAdvisor.Reading fd = PostflopAdvisor.classify(cards("Ah", "7h"), cards("Kh", "9h", "2c"));
        assertEquals(PostflopAdvisor.HandClass.DRAW, fd.handClass());
        assertEquals(9, fd.outs());
        PostflopAdvisor.Reading combo = PostflopAdvisor.classify(cards("8h", "9h"), cards("7h", "6h", "Kc"));
        assertEquals(PostflopAdvisor.HandClass.DRAW, combo.handClass());
        assertTrue(combo.comboDraw());
        assertEquals(15, combo.outs());
        // 리버(보드 5장)에는 드로우 분류가 없다
        assertEquals(PostflopAdvisor.HandClass.AIR,
                PostflopAdvisor.classify(cards("Ah", "7h"), cards("Kh", "9h", "2c", "3s", "Jd")).handClass());
    }

    @Test
    void classifiesFlopTexture() {
        assertEquals(PostflopAdvisor.Texture.DRY, PostflopAdvisor.texture(cards("Qc", "6h", "2s")));
        assertEquals(PostflopAdvisor.Texture.WET, PostflopAdvisor.texture(cards("Jh", "Th", "9c")));
        assertEquals(PostflopAdvisor.Texture.WET, PostflopAdvisor.texture(cards("Ah", "7h", "2h")));
        assertEquals(PostflopAdvisor.Texture.PAIRED, PostflopAdvisor.texture(cards("8c", "8h", "2s")));
        assertEquals(PostflopAdvisor.Texture.ACE_HIGH, PostflopAdvisor.texture(cards("Ac", "7h", "2s")));
    }

    /* ---------- 플랍 판단 ---------- */

    @Test
    void cbetsAirOnDryFlopAsAggressor() {
        HandEngine e = flop("Ah", "5c", "Kd", "3s", "Qs", "6d", "2h");
        e.apply(Action.check("me"));
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("BET", d.orElseThrow().type());
        assertEquals(60, d.get().amount(), "마른 보드 C-벳은 1/2팟");
        assertTrue(d.get().reason().contains("C-벳"));
    }

    @Test
    void checksAirOnWetFlop() {
        HandEngine e = flop("Ad", "2s", "Kd", "3s", "Jh", "Th", "9c");
        e.apply(Action.check("me"));
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("CHECK", d.orElseThrow().type());
        assertTrue(d.get().reason().contains("젖은 보드"));
    }

    @Test
    void valueBetsTopPair() {
        HandEngine e = flop("As", "Qd", "Kd", "3s", "Qc", "6h", "2h");
        e.apply(Action.check("me"));
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("BET", d.orElseThrow().type());
        assertTrue(d.get().reason().contains("밸류벳"));
    }

    @Test
    void mediumHandChecksThenCallsSmallFoldsBig() {
        // 77 on Q62 = 미디엄: 체크
        HandEngine e = flop("7s", "7d", "Kd", "3s", "Qc", "6h", "2h");
        e.apply(Action.check("me"));
        assertEquals("CHECK", advisor.advise(e, "bot", new Random(1)).orElseThrow().type());

        // 작은 벳(30 ≤ 팟 120 의 1/3)은 콜
        HandEngine e2 = flop("7s", "7d", "Kd", "3s", "Qc", "6h", "2h");
        e2.apply(Action.bet("me", 30));
        assertEquals("CALL", advisor.advise(e2, "bot", new Random(1)).orElseThrow().type());

        // 팟사이즈 벳은 폴드
        HandEngine e3 = flop("7s", "7d", "Kd", "3s", "Qc", "6h", "2h");
        e3.apply(Action.bet("me", 120));
        assertEquals("FOLD", advisor.advise(e3, "bot", new Random(1)).orElseThrow().type());
    }

    @Test
    void flushDrawCallsHalfPotFoldsPotSize() {
        // FD 9아웃: 플랍 룰오브4 = 36% + 임플라이드 4% = 40%
        HandEngine e = flop("Ah", "7h", "Kd", "3s", "Kh", "9h", "2c");
        e.apply(Action.bet("me", 60)); // 필요 33% → 콜
        assertEquals("CALL", advisor.advise(e, "bot", new Random(1)).orElseThrow().type());

        HandEngine e2 = flop("Ah", "7h", "Kd", "3s", "Kh", "9h", "2c");
        e2.apply(Action.bet("me", 300)); // 필요 42% > 40% → 폴드
        assertEquals("FOLD", advisor.advise(e2, "bot", new Random(1)).orElseThrow().type());
    }

    @Test
    void comboDrawSemiBluffRaisesFlop() {
        HandEngine e = flop("8h", "9h", "Kd", "3s", "7h", "6h", "Kc");
        e.apply(Action.bet("me", 60));
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("RAISE", d.orElseThrow().type());
        assertTrue(d.get().reason().contains("콤보 드로우"));
    }

    @Test
    void alarmFoldsTopPairWhenRaisedOverMyBet() {
        HandEngine e = flop("As", "Qd", "Kd", "3s", "Qc", "6h", "2h");
        e.apply(Action.check("me"));
        e.apply(Action.bet("bot", 60));
        e.apply(Action.raiseTo("me", 180)); // 내 벳에 레이즈 = 경보
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("FOLD", d.orElseThrow().type());
        assertTrue(d.get().reason().contains("경보"));
    }

    @Test
    void monsterRaisesFacingBet() {
        HandEngine e = flop("8s", "9d", "Kd", "3s", "7c", "6h", "Th");
        e.apply(Action.bet("me", 60));
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("RAISE", d.orElseThrow().type());
        assertTrue(d.get().reason().contains("괴물"));
    }

    /* ---------- 턴/리버 규칙 ---------- */

    @Test
    void turnPotControlsOnePairAndRiverThinValues() {
        // 플랍 밸류벳 → 콜 → 턴: 원페어 팟 컨트롤 1/2팟
        HandEngine e = flop("As", "Qd", "Kd", "3s", "Qc", "6h", "2h");
        e.apply(Action.check("me"));
        e.apply(Action.bet("bot", 60));
        e.apply(Action.call("me")); // 턴(2d), 팟 240
        assertEquals(Street.TURN, e.street());
        e.apply(Action.check("me"));
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("BET", d.orElseThrow().type());
        assertEquals(120, d.get().amount(), "턴 팟 컨트롤 = 1/2팟");
        assertTrue(d.get().reason().contains("팟 컨트롤"));

        // 리버(3c — 스케어 아님): 씬 밸류 1/2팟
        e.apply(Action.bet("bot", 120));
        e.apply(Action.call("me")); // 리버, 팟 480
        assertEquals(Street.RIVER, e.street());
        e.apply(Action.check("me"));
        Optional<BotBrain.Decision> d2 = advisor.advise(e, "bot", new Random(1));
        assertEquals("BET", d2.orElseThrow().type());
        assertTrue(d2.get().reason().contains("씬 밸류"));
    }

    @Test
    void riverFoldsOnePairToBigBet() {
        HandEngine e = flop("As", "Qd", "Kd", "3s", "Qc", "6h", "2h");
        e.apply(Action.check("me"));
        e.apply(Action.check("bot")); // 플랍 체크 통과
        e.apply(Action.check("me"));
        e.apply(Action.check("bot")); // 턴 체크 통과, 리버 팟 120
        assertEquals(Street.RIVER, e.street());
        e.apply(Action.bet("me", 120)); // 팟사이즈 = 큰 벳
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("FOLD", d.orElseThrow().type());
        assertTrue(d.get().reason().contains("리버 큰 벳"));
    }

    /* ---------- 리버 밸류벳: 모두 체크로 온 팟 (poker_ai.png 사례 재현) ---------- */

    /**
     * 3인 림프 팟(딜 순서 = SB,BB,BTN 라운드×2): 프리플랍 전원 림프 → 플랍·턴 전원 체크.
     * 보드 Qs 6d 2s 5d Ks — 스페이드 3장(스케어)이지만 아무도 벳한 적이 없다.
     * bot(BTN) = AhKc 톱페어 톱키커.
     */
    private static HandEngine limpedToRiver3way() {
        Deck deck = Deck.ofOrder(cards(
                "5h", "Td", "Ah", "3h", "9d", "Kc", // 홀카드: p2, p3, bot 순 ×2
                "Qs", "6d", "2s", "5d", "Ks"));
        HandEngine e = new HandEngine(List.of(
                new Player("bot", "Bot", 1000),   // index 0 = 버튼(BTN)
                new Player("p2", "P2", 1000),     // SB
                new Player("p3", "P3", 1000)),    // BB
                0, 10, 20, deck);
        e.start();
        e.apply(Action.call("bot"));
        e.apply(Action.call("p2"));
        e.apply(Action.check("p3"));  // 플랍 Qs 6d 2s, 팟 60
        e.apply(Action.check("p2"));
        e.apply(Action.check("p3"));
        e.apply(Action.check("bot")); // 턴 5d
        e.apply(Action.check("p2"));
        e.apply(Action.check("p3"));
        e.apply(Action.check("bot")); // 리버 Ks
        e.apply(Action.check("p2"));
        e.apply(Action.check("p3"));
        return e; // bot 차례
    }

    // 스케어 보드 + 멀티웨이라도, 모두 체크로 온 리버의 톱페어는 밸류벳해야 한다
    @Test
    void betsTopPairOnRiverWhenPotCheckedThrough() {
        HandEngine e = limpedToRiver3way();
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("BET", d.orElseThrow().type());
        assertEquals(30, d.get().amount(), "팟 60 의 1/2");
        assertTrue(d.get().reason().contains("모두 체크로 온 리버"), d.get().reason());
    }

    // 같은 밸류 스팟이라도 100% 고정이 아니라 riverValueFreq 빈도로 섞는다(착취 방지) —
    // 빈도 0 이면 체크로 범위를 위장한다
    @Test
    void riverValueBetIsFrequencyMixedNotFixed() {
        PostflopAdvisor never = new PostflopAdvisor(1.0, 1.0, 0.0);
        HandEngine e = limpedToRiver3way();
        Optional<BotBrain.Decision> d = never.advise(e, "bot", new Random(1));
        assertEquals("CHECK", d.orElseThrow().type());
        assertTrue(d.get().reason().contains("빈도 혼합"), d.get().reason());
    }

    // 반대로 앞 스트리트에 실제 액션(벳)이 있었던 팟이면 스케어 보드 경계는 유지된다
    @Test
    void checksTopPairOnScaryRiverAfterRealAction() {
        Deck deck = Deck.ofOrder(cards(
                "5h", "Td", "Kc", "3h", "9d", "Jc", // bot = KcJc
                "Qs", "6d", "2s", "5d", "Ks"));
        HandEngine e = new HandEngine(List.of(
                new Player("bot", "Bot", 1000),
                new Player("p2", "P2", 1000),
                new Player("p3", "P3", 1000)),
                0, 10, 20, deck);
        e.start();
        e.apply(Action.call("bot"));
        e.apply(Action.call("p2"));
        e.apply(Action.check("p3"));   // 플랍
        e.apply(Action.bet("p2", 30)); // 앞 스트리트 실제 액션
        e.apply(Action.fold("p3"));
        e.apply(Action.call("bot"));   // 턴
        e.apply(Action.check("p2"));
        e.apply(Action.check("bot"));  // 리버 Ks — bot 톱페어
        e.apply(Action.check("p2"));
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("CHECK", d.orElseThrow().type());
        assertTrue(d.get().reason().contains("스케어"), d.get().reason());
    }

    /* ---------- BotBrain 통합 ---------- */

    @Test
    void botBrainUsesHarringtonRulesPostflop() {
        BotBrain brain = new BotBrain(new EquityService(), PreflopAdvisor.disabled(),
                new PostflopAdvisor(1.0, 1.0, 1.0));
        HandEngine e = flop("Ah", "5c", "Kd", "3s", "Qs", "6d", "2h");
        e.apply(Action.check("me"));
        BotBrain.Decision d = brain.decide(e, "bot", 100, new Random(1));
        assertTrue(d.reason().startsWith("해링턴:"), "포스트플랍은 해링턴 규칙: " + d.reason());
    }

    /* ---------- 상대 모델링(니트/콜스테이션/LAG) ---------- */

    private static com.homepoker.stats.StatsService statsWith(String id, boolean vpip, boolean pfr,
                                                              int aggrPerHand, int callsPerHand) {
        var stats = new com.homepoker.stats.StatsService();
        for (int i = 0; i < 30; i++) {
            stats.record(new com.homepoker.stats.HandReport(
                    java.util.Map.of(id, id), java.util.Set.of(id),
                    vpip ? java.util.Set.of(id) : java.util.Set.of(),
                    pfr ? java.util.Set.of(id) : java.util.Set.of(),
                    java.util.Map.of(id, 0L), java.util.Set.of(), java.util.Set.of(id),
                    java.util.Map.of(id, aggrPerHand), java.util.Map.of(id, callsPerHand),
                    java.util.Set.of(), java.util.Set.of(), java.util.Set.of()));
        }
        return stats;
    }

    @Test
    void neverBluffsCallingStation() {
        // me = 콜스테이션(VPIP 100%, AF 0): 에어 마른 보드라도 C-벳 금지
        PostflopAdvisor a = new PostflopAdvisor(statsWith("me", true, false, 0, 2), 1.0, 1.0, 1.0);
        HandEngine e = flop("Ah", "5c", "Kd", "3s", "Qs", "6d", "2h");
        e.apply(Action.check("me"));
        Optional<BotBrain.Decision> d = a.advise(e, "bot", new Random(1));
        assertEquals("CHECK", d.orElseThrow().type());
        assertTrue(d.get().reason().contains("블러프 금지"));
    }

    @Test
    void callsDownVsLagRaise() {
        // me = LAG(VPIP/PFR 100%, AF 3): 내 벳에 레이즈 맞아도 역해석 → 콜다운
        PostflopAdvisor a = new PostflopAdvisor(statsWith("me", true, true, 3, 1), 1.0, 1.0, 1.0);
        HandEngine e = flop("As", "Qd", "Kd", "3s", "Qc", "6h", "2h");
        e.apply(Action.check("me"));
        e.apply(Action.bet("bot", 60));
        e.apply(Action.raiseTo("me", 180));
        Optional<BotBrain.Decision> d = a.advise(e, "bot", new Random(1));
        assertEquals("CALL", d.orElseThrow().type());
        assertTrue(d.get().reason().contains("LAG"));
    }

    @Test
    void foldsOnePairToNitTurnBet() {
        // me = 니트(VPIP 0%): 니트의 턴 큰 베팅 = 진짜 → 톱페어 폴드
        PostflopAdvisor a = new PostflopAdvisor(statsWith("me", false, false, 0, 0), 1.0, 1.0, 1.0);
        HandEngine e = flop("As", "Qd", "Kd", "3s", "Qc", "6h", "2h");
        e.apply(Action.check("me"));
        e.apply(Action.check("bot")); // 플랍 체크 통과 → 턴 팟 120
        e.apply(Action.bet("me", 120));
        Optional<BotBrain.Decision> d = a.advise(e, "bot", new Random(1));
        assertEquals("FOLD", d.orElseThrow().type());
        assertTrue(d.get().reason().contains("니트"));
    }

    @Test
    void disabledAdvisorAlwaysFallsBack() {
        HandEngine e = flop("Ah", "5c", "Kd", "3s", "Qs", "6d", "2h");
        e.apply(Action.check("me"));
        assertTrue(PostflopAdvisor.disabled().advise(e, "bot", new Random(1)).isEmpty());
    }
}
