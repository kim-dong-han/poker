package com.homepoker.bot;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.card.Deck;
import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.Player;
import com.homepoker.range.BtsPreflopCharts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 차트 기반 프리플랍 판단 검증. 실제 전사 데이터(저작권상 레포 미포함) 대신
 * 테스트 전용의 자작 미니 차트(bts-preflop-test.json)를 쓴다 — 항상 실행 가능.
 */
class PreflopAdvisorTest {

    private static final PreflopAdvisor advisor = new PreflopAdvisor(
            new BtsPreflopCharts("preflop/bts-preflop-test.json"), 0.35);

    private static List<Card> cards(String... notations) {
        List<Card> list = new ArrayList<>();
        for (String s : notations) {
            list.add(Card.of(s));
        }
        return list;
    }

    /** 헤즈업: index 0 = 버튼 = SB, 딜 순서는 SB→BB 교차. */
    private static HandEngine headsUp(String sbC1, String bbC1, String sbC2, String bbC2) {
        Player a = new Player("bot", "Bot", 1000);
        Player b = new Player("me", "Me", 1000);
        Deck deck = Deck.ofOrder(cards(sbC1, bbC1, sbC2, bbC2, "Ks", "Qs", "Js", "5d", "9c"));
        HandEngine e = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        e.start();
        return e;
    }

    @Test
    void opensChartHandFromSb() {
        HandEngine e = headsUp("As", "7c", "Ah", "2d"); // bot(SB) = AA
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertTrue(d.isPresent());
        assertEquals("RAISE", d.get().type());
        assertEquals(60, d.get().amount(), "SB 오픈 3bb = 60"); // 3.0bb * 20
        assertTrue(d.get().reason().contains("오픈 레인지"));
    }

    @Test
    void foldsHandOutsideOpenChart() {
        HandEngine e = headsUp("7h", "Ac", "2c", "Ad"); // bot(SB) = 72o — 차트 밖
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertTrue(d.isPresent());
        assertEquals("FOLD", d.get().type());
    }

    @Test
    void mixedHandOpensAtItsFrequency() {
        int raises = 0;
        Random rng = new Random(42); // 시드별 첫 nextDouble 은 상관되므로 하나의 난수원을 이어서 쓴다
        for (int trial = 0; trial < 300; trial++) {
            HandEngine e = headsUp("5h", "Ac", "4h", "Ad"); // bot(SB) = 54s (open 0.5)
            Optional<BotBrain.Decision> d = advisor.advise(e, "bot", rng);
            if (d.orElseThrow().type().equals("RAISE")) {
                raises++;
            }
        }
        assertTrue(raises > 90 && raises < 210, "54s 는 약 50% 빈도로 오픈해야 한다: " + raises);
    }

    @Test
    void bbDefendsByChartThreeBetCallFold() {
        // bot 이 BB: index 0 = me(버튼/SB) 가 3bb 오픈
        Player me = new Player("me", "Me", 1000);
        Player bot = new Player("bot", "Bot", 1000);
        Deck deck = Deck.ofOrder(cards("7c", "As", "2d", "Ah", "Ks", "Qs", "Js", "5d", "9c"));
        HandEngine e = new HandEngine(List.of(me, bot), 0, 10, 20, deck);
        e.start();
        e.apply(Action.raiseTo("me", 60));

        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1)); // AA → 3벳
        assertEquals("RAISE", d.orElseThrow().type());
        assertEquals(180, d.get().amount(), "BB 3벳 9bb = 180");

        // 55 → 콜
        Deck deck2 = Deck.ofOrder(cards("7c", "5s", "2d", "5h", "Ks", "Qs", "Js", "5d", "9c"));
        HandEngine e2 = new HandEngine(List.of(new Player("me", "Me", 1000), new Player("bot", "Bot", 1000)),
                0, 10, 20, deck2);
        e2.start();
        e2.apply(Action.raiseTo("me", 60));
        assertEquals("CALL", advisor.advise(e2, "bot", new Random(1)).orElseThrow().type());

        // 72o → 폴드
        Deck deck3 = Deck.ofOrder(cards("Ac", "7s", "Ad", "2h", "Ks", "Qs", "Js", "5d", "9c"));
        HandEngine e3 = new HandEngine(List.of(new Player("me", "Me", 1000), new Player("bot", "Bot", 1000)),
                0, 10, 20, deck3);
        e3.start();
        e3.apply(Action.raiseTo("me", 60));
        assertEquals("FOLD", advisor.advise(e3, "bot", new Random(1)).orElseThrow().type());
    }

    @Test
    void fourBetsWithChartHandAfterBeingThreeBet() {
        HandEngine e = headsUp("As", "7c", "Ah", "2d"); // bot(SB) = AA
        e.apply(Action.raiseTo("bot", 60));  // 오픈
        e.apply(Action.raiseTo("me", 180));  // BB 3벳
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("RAISE", d.orElseThrow().type());
        assertEquals(460, d.get().amount(), "SB vs BB 4벳 23bb = 460");
    }

    @Test
    void fiveBetShovesWithChartHandAfterBeingFourBet() {
        // bot 이 BB 에서 3벳 → SB(me) 4벳 → bot 5벳 올인
        Player me = new Player("me", "Me", 1000);
        Player bot = new Player("bot", "Bot", 1000);
        Deck deck = Deck.ofOrder(cards("7c", "As", "2d", "Ah", "Ks", "Qs", "Js", "5d", "9c"));
        HandEngine e = new HandEngine(List.of(me, bot), 0, 10, 20, deck);
        e.start();
        e.apply(Action.raiseTo("me", 60));
        e.apply(Action.raiseTo("bot", 180));
        e.apply(Action.raiseTo("me", 460));
        Optional<BotBrain.Decision> d = advisor.advise(e, "bot", new Random(1));
        assertEquals("RAISE", d.orElseThrow().type());
        assertEquals(1000, d.get().amount(), "5벳은 100bb 스택에서 올인");
    }

    @Test
    void emptyChartsAlwaysFallBack() {
        HandEngine e = headsUp("As", "7c", "Ah", "2d");
        assertTrue(PreflopAdvisor.disabled().advise(e, "bot", new Random(1)).isEmpty());
    }

    @Test
    void positionsMapCoversTwoToSixPlayers() {
        List<Player> six = List.of(
                new Player("a", "a", 100), new Player("b", "b", 100), new Player("c", "c", 100),
                new Player("d", "d", 100), new Player("e", "e", 100), new Player("f", "f", 100));
        Map<String, String> p6 = PreflopAdvisor.positions(six, 0);
        assertEquals("BTN", p6.get("a")); // a=버튼
        assertEquals("SB", p6.get("b"));
        assertEquals("BB", p6.get("c"));
        assertEquals("UTG", p6.get("d"));
        assertEquals("MP", p6.get("e"));
        assertEquals("CO", p6.get("f"));

        Map<String, String> p4 = PreflopAdvisor.positions(six.subList(0, 4), 0);
        assertEquals("SB", p4.get("b"));
        assertEquals("BB", p4.get("c"));
        assertEquals("CO", p4.get("d"));
        // 4인 테이블의 버튼은 그대로 BTN

        Map<String, String> p2 = PreflopAdvisor.positions(six.subList(0, 2), 0);
        assertEquals("SB", p2.get("a"));
        assertEquals("BB", p2.get("b"));
        assertFalse(p2.containsKey("c"));
    }
}
