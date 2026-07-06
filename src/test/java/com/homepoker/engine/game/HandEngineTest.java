package com.homepoker.engine.game;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.card.Deck;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandEngineTest {

    private static List<Card> cards(String... notations) {
        List<Card> list = new ArrayList<>();
        for (String s : notations) {
            list.add(Card.of(s));
        }
        return list;
    }

    private static long totalStacks(List<Player> players) {
        return players.stream().mapToLong(Player::stack).sum();
    }

    // ---- 헤즈업 체크다운: 높은 페어가 이긴다 ----
    @Test
    void headsUpCheckdownHigherPairWins() {
        Player a = new Player("A", "Alice", 1000);
        Player b = new Player("B", "Bob", 1000);
        // 헤즈업: 버튼=SB=A. 딜 순서 SB우선: A,B,A,B → A=As,Ah / B=Kd,Kc
        Deck deck = Deck.ofOrder(cards("As", "Kd", "Ah", "Kc", "2c", "7d", "9h", "Js", "3c"));
        HandEngine engine = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        engine.start();

        // 프리플랍: A(SB)가 먼저
        assertEquals("A", engine.playerToAct().id());
        engine.apply(Action.call("A"));   // 10 더 → 20
        engine.apply(Action.check("B"));  // BB 옵션 체크 → 플랍

        assertEquals(Street.FLOP, engine.street());
        // 포스트플랍: B(BB)가 먼저
        engine.apply(Action.check("B"));
        engine.apply(Action.check("A"));
        engine.apply(Action.check("B")); // 턴
        engine.apply(Action.check("A"));
        engine.apply(Action.check("B")); // 리버
        engine.apply(Action.check("A"));

        assertTrue(engine.isComplete());
        assertEquals(40L, engine.payouts().get("A")); // A가 팟 40 획득
        assertEquals(1020L, a.stack());
        assertEquals(980L, b.stack());
        assertEquals(2000L, totalStacks(List.of(a, b)));
    }

    // ---- 폴드하면 상대가 무혈입성 ----
    @Test
    void foldGivesPotToOpponent() {
        Player a = new Player("A", "Alice", 1000);
        Player b = new Player("B", "Bob", 1000);
        Deck deck = Deck.ofOrder(cards("As", "Kd", "Ah", "Kc"));
        HandEngine engine = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        engine.start();

        engine.apply(Action.fold("A")); // SB 폴드
        assertTrue(engine.isComplete());
        assertEquals(30L, engine.payouts().get("B")); // sb10 + bb20
        assertEquals(990L, a.stack());
        assertEquals(1010L, b.stack());
    }

    // ---- 3인 올인: 메인팟/사이드팟 분배 ----
    @Test
    void threeWayAllInSplitsMainAndSidePot() {
        Player a = new Player("A", "Alice", 100);
        Player b = new Player("B", "Bob", 100);
        Player c = new Player("C", "Carol", 40);
        // n=3, 버튼=0 → SB=1(B), BB=2(C). 딜 SB우선: B,C,A,B,C,A
        Deck deck = Deck.ofOrder(cards(
                "Qs", "As", "Ks", "Qh", "Ah", "Kh", // 홀: B=QsQh, C=AsAh, A=KsKh
                "2c", "7d", "9h", "Jd", "3s"));      // 보드(도움 없음)
        HandEngine engine = new HandEngine(List.of(a, b, c), 0, 10, 20, deck);
        engine.start();

        // 프리플랍 첫 액션 = BB 다음 = A
        assertEquals("A", engine.playerToAct().id());
        engine.apply(Action.raiseTo("A", 100)); // A 올인 100
        engine.apply(Action.call("B"));         // B 콜 → 올인 100
        engine.apply(Action.call("C"));         // C 콜 → 올인 40 (숏)

        assertTrue(engine.isComplete());
        List<Pot> pots = engine.pots();
        assertEquals(2, pots.size());
        assertEquals(120L, pots.get(0).amount()); // 메인 40*3
        assertEquals(120L, pots.get(1).amount()); // 사이드 60*2

        // C(AA)가 메인, A(KK)가 사이드, B(QQ) 꽝
        assertEquals(120L, engine.payouts().get("C"));
        assertEquals(120L, engine.payouts().get("A"));
        assertEquals(null, engine.payouts().get("B"));
        assertEquals(120L, a.stack());
        assertEquals(0L, b.stack());
        assertEquals(120L, c.stack());
        assertEquals(240L, totalStacks(List.of(a, b, c)));
    }

    // ---- 규칙 검증 ----
    @Test
    void rejectsBelowMinRaise() {
        Player a = new Player("A", "Alice", 1000);
        Player b = new Player("B", "Bob", 1000);
        Deck deck = Deck.ofOrder(cards("As", "Kd", "Ah", "Kc"));
        HandEngine engine = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        engine.start();
        // currentBet=20, 최소 레이즈-투=40. 30은 불가.
        assertThrows(IllegalArgumentException.class, () -> engine.apply(Action.raiseTo("A", 30)));
    }

    @Test
    void rejectsCheckWhenFacingBet() {
        Player a = new Player("A", "Alice", 1000);
        Player b = new Player("B", "Bob", 1000);
        Deck deck = Deck.ofOrder(cards("As", "Kd", "Ah", "Kc"));
        HandEngine engine = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        engine.start();
        // A(SB)는 10을 더 맞춰야 하므로 체크 불가
        assertThrows(IllegalArgumentException.class, () -> engine.apply(Action.check("A")));
    }

    @Test
    void rejectsActingOutOfTurn() {
        Player a = new Player("A", "Alice", 1000);
        Player b = new Player("B", "Bob", 1000);
        Deck deck = Deck.ofOrder(cards("As", "Kd", "Ah", "Kc"));
        HandEngine engine = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        engine.start();
        assertThrows(IllegalStateException.class, () -> engine.apply(Action.check("B")));
    }

    @Test
    void legalActionsPreflopForFirstActor() {
        Player a = new Player("A", "Alice", 1000);
        Player b = new Player("B", "Bob", 1000);
        Deck deck = Deck.ofOrder(cards("As", "Kd", "Ah", "Kc"));
        HandEngine engine = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        engine.start();
        assertEquals(
                java.util.Set.of(ActionType.FOLD, ActionType.CALL, ActionType.RAISE),
                engine.legalActions("A"));
    }

    // ---- 속성 테스트: 무작위 자동플레이 수백 판, 칩 보존 + 반드시 종료 ----
    @Test
    void randomizedPlayConservesChipsAndAlwaysTerminates() {
        long initial = 1000L * 4;
        java.util.Random rng = new java.util.Random(20260706L);
        for (int hand = 0; hand < 300; hand++) {
            List<Player> players = List.of(
                    new Player("A", "A", 1000),
                    new Player("B", "B", 1000),
                    new Player("C", "C", 1000),
                    new Player("D", "D", 1000));
            Deck deck = Deck.shuffled(new Random(rng.nextLong()));
            HandEngine engine = new HandEngine(players, hand % 4, 10, 20, deck);
            engine.start();

            int guard = 0;
            while (!engine.isComplete()) {
                if (guard++ > 500) {
                    throw new AssertionError("핸드가 종료되지 않음(무한 루프 의심)");
                }
                // 진행 중 불변식: 스택 합 + 팟 = 초기 총량
                assertEquals(initial, totalStacks(players) + engine.pot());

                Player actor = engine.playerToAct();
                var legal = engine.legalActions(actor.id());
                Action action = chooseAction(engine, actor, legal, rng);
                engine.apply(action);
            }
            // 종료 후: 팟은 스택으로 되돌아옴
            assertEquals(initial, totalStacks(players),
                    "hand=" + hand + " 에서 칩 총량 불일치");
        }
    }

    /** 항상 유효한 액션만 고르는 단순 봇(콜/체크 위주, 가끔 올인/폴드). */
    private static Action chooseAction(HandEngine engine, Player actor,
                                       java.util.Set<ActionType> legal, java.util.Random rng) {
        int roll = rng.nextInt(100);
        long committed = engine.currentBet() - engine.amountToCall(actor.id());
        if (roll < 8 && legal.contains(ActionType.BET)) {
            return Action.bet(actor.id(), actor.stack()); // 올인 벳
        }
        if (roll < 8 && legal.contains(ActionType.RAISE)) {
            return Action.raiseTo(actor.id(), committed + actor.stack()); // 올인 레이즈
        }
        if (roll < 12 && legal.contains(ActionType.FOLD) && !legal.contains(ActionType.CHECK)) {
            return Action.fold(actor.id());
        }
        if (legal.contains(ActionType.CHECK)) {
            return Action.check(actor.id());
        }
        if (legal.contains(ActionType.CALL)) {
            return Action.call(actor.id());
        }
        return Action.fold(actor.id());
    }

    @Test
    void boardIsRevealedProgressively() {
        Player a = new Player("A", "Alice", 1000);
        Player b = new Player("B", "Bob", 1000);
        Deck deck = Deck.ofOrder(cards("As", "Kd", "Ah", "Kc", "2c", "7d", "9h", "Js", "3c"));
        HandEngine engine = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        engine.start();
        assertEquals(0, engine.board().size());
        engine.apply(Action.call("A"));
        engine.apply(Action.check("B"));
        assertEquals(3, engine.board().size()); // 플랍
        engine.apply(Action.check("B"));
        engine.apply(Action.check("A"));
        assertEquals(4, engine.board().size()); // 턴
        assertFalse(engine.isComplete());
        assertEquals(Arrays.asList(Card.of("2c"), Card.of("7d"), Card.of("9h"), Card.of("Js")),
                engine.board());
    }
}
