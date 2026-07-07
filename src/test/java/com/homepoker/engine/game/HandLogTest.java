package com.homepoker.engine.game;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.card.Deck;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandLogTest {

    private static List<Card> cards(String... notations) {
        List<Card> list = new ArrayList<>();
        for (String s : notations) {
            list.add(Card.of(s));
        }
        return list;
    }

    // ---- 대본 핸드: 기록을 재생하면 원본과 동일한 종료 상태가 나온다 ----
    @Test
    void replayReproducesScriptedHand() {
        Player a = new Player("A", "Alice", 1000);
        Player b = new Player("B", "Bob", 1000);
        Deck deck = Deck.ofOrder(cards("As", "Kd", "Ah", "Kc", "2c", "7d", "9h", "Js", "3c"));
        HandEngine engine = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        engine.start();
        engine.apply(Action.call("A"));
        engine.apply(Action.check("B"));
        engine.apply(Action.check("B"));
        engine.apply(Action.check("A"));
        engine.apply(Action.check("B"));
        engine.apply(Action.check("A"));
        engine.apply(Action.check("B"));
        engine.apply(Action.check("A"));
        assertTrue(engine.isComplete());

        HandLog log = engine.log();
        assertEquals(8, log.actionCount());

        HandEngine replay = log.finalState();
        assertTrue(replay.isComplete());
        assertEquals(engine.board(), replay.board());
        assertEquals(engine.payouts(), replay.payouts());
        assertEquals(engine.street(), replay.street());
    }

    // ---- 중간 시점 복원: stateAt(k) 는 원본을 k개 액션까지 진행한 상태와 같다 ----
    @Test
    void stateAtReconstructsIntermediateStreets() {
        Player a = new Player("A", "Alice", 1000);
        Player b = new Player("B", "Bob", 1000);
        Deck deck = Deck.ofOrder(cards("As", "Kd", "Ah", "Kc", "2c", "7d", "9h", "Js", "3c"));
        HandEngine engine = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        engine.start();
        engine.apply(Action.call("A"));
        engine.apply(Action.check("B"));
        HandLog log = engine.log();

        HandEngine preflop = log.stateAt(0);       // 딜·블라인드 직후
        assertEquals(Street.PREFLOP, preflop.street());
        assertEquals(0, preflop.board().size());
        assertEquals("A", preflop.playerToAct().id());

        HandEngine afterFlopDealt = log.stateAt(2); // 콜+체크 후 → 플랍
        assertEquals(Street.FLOP, afterFlopDealt.street());
        assertEquals(3, afterFlopDealt.board().size());
    }

    // ---- 속성 테스트: 무작위 자동플레이를 기록 후 재생하면 항상 동일한 결과 ----
    @Test
    void replayMatchesRandomizedPlay() {
        Random rng = new Random(20260707L);
        for (int hand = 0; hand < 200; hand++) {
            List<Player> players = List.of(
                    new Player("A", "A", 1000),
                    new Player("B", "B", 1000),
                    new Player("C", "C", 1000),
                    new Player("D", "D", 1000));
            Deck deck = Deck.shuffled(new Random(rng.nextLong()));
            HandEngine engine = new HandEngine(players, hand % 4, 10, 20, deck);
            engine.start();
            while (!engine.isComplete()) {
                Player actor = engine.playerToAct();
                Action action = chooseAction(engine, actor, rng);
                engine.apply(action);
            }

            HandLog log = engine.log();
            HandEngine replay = log.finalState();
            assertEquals(engine.board(), replay.board(), "hand=" + hand + " 보드 불일치");
            assertEquals(engine.payouts(), replay.payouts(), "hand=" + hand + " 분배 불일치");
            List<Long> orig = engine.players().stream().map(Player::stack).toList();
            List<Long> rep = replay.players().stream().map(Player::stack).toList();
            assertEquals(orig, rep, "hand=" + hand + " 최종 스택 불일치");
        }
    }

    private static Action chooseAction(HandEngine engine, Player actor, Random rng) {
        var legal = engine.legalActions(actor.id());
        int roll = rng.nextInt(100);
        long committed = engine.currentBet() - engine.amountToCall(actor.id());
        if (roll < 8 && legal.contains(ActionType.BET)) {
            return Action.bet(actor.id(), actor.stack());
        }
        if (roll < 8 && legal.contains(ActionType.RAISE)) {
            return Action.raiseTo(actor.id(), committed + actor.stack());
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
}
