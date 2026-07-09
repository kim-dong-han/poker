package com.homepoker.stats;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.card.Deck;
import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 종료 핸드 로그 재생으로 AF/WtSD/F3B 원료를 뽑는 집계 검증. */
class HandLogTallyTest {

    private static List<Card> cards(String... notations) {
        List<Card> list = new ArrayList<>();
        for (String s : notations) {
            list.add(Card.of(s));
        }
        return list;
    }

    /** 헤즈업: a = 버튼/SB, b = BB. */
    private static HandEngine headsUp() {
        Player a = new Player("a", "A", 1000);
        Player b = new Player("b", "B", 1000);
        Deck deck = Deck.ofOrder(cards("As", "Kd", "Ah", "Kc", "Qs", "6d", "2h", "4d", "3c"));
        HandEngine e = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        e.start();
        return e;
    }

    @Test
    void tracksFoldToThreeBet() {
        HandEngine e = headsUp();
        e.apply(Action.raiseTo("a", 60));   // a 오픈
        e.apply(Action.raiseTo("b", 180));  // b 3벳
        e.apply(Action.fold("a"));          // a 폴드 = F3B
        HandLogTally.Tally t = HandLogTally.tally(e.log());
        assertTrue(t.facedThreeBet().contains("a"));
        assertTrue(t.foldedToThreeBet().contains("a"));
        assertFalse(t.facedThreeBet().contains("b"));
        assertTrue(t.sawFlop().isEmpty(), "프리플랍 종료 — 플랍 없음");
    }

    @Test
    void tracksPostflopCountsAndShowdown() {
        HandEngine e = headsUp();
        e.apply(Action.raiseTo("a", 60));
        e.apply(Action.call("b"));          // 플랍
        e.apply(Action.bet("b", 30));
        e.apply(Action.call("a"));          // 턴
        e.apply(Action.check("b"));
        e.apply(Action.check("a"));         // 리버
        e.apply(Action.check("b"));
        e.apply(Action.check("a"));         // 쇼다운
        HandLogTally.Tally t = HandLogTally.tally(e.log());
        assertEquals(java.util.Set.of("a", "b"), t.sawFlop());
        assertEquals(1, t.postflopAggr().getOrDefault("b", 0));
        assertEquals(1, t.postflopCalls().getOrDefault("a", 0));
        assertEquals(0, t.postflopAggr().getOrDefault("a", 0));
        assertEquals(java.util.Set.of("a", "b"), t.showdown());
        assertTrue(t.facedThreeBet().isEmpty());
    }
}
