package com.homepoker.review;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.card.Deck;
import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.HandLog;
import com.homepoker.engine.game.Player;
import com.homepoker.equity.EquityService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandReviewerTest {

    // ---------------------------------------------------------------- 순수 판정(judge)

    // 경계: 이퀴티가 정확히 팟오즈와 같으면 실수가 아니다(EV 손실 0).
    @Test
    void equityExactlyAtPotOddsIsNotMistake() {
        // 팟 60, 콜 20 → 필요이퀴티 = 20/80 = 0.25
        DecisionReview call = HandReviewer.judge(0, "A", "Alice", "TURN",
                ActionType.CALL, 0.25, 60, 20, 20);
        assertFalse(call.mistake());
        assertEquals(0.0, call.evLossBb());
        assertEquals(0.25, call.requiredEquity(), 1e-9);

        DecisionReview fold = HandReviewer.judge(0, "A", "Alice", "TURN",
                ActionType.FOLD, 0.25, 60, 20, 20);
        assertFalse(fold.mistake());
        assertEquals(0.0, fold.evLossBb());
    }

    // 여유폭(MARGIN) 이내의 근소한 손해는 몬테카를로 오차로 보고 실수로 치지 않는다.
    @Test
    void gapWithinMarginIsNotMistake() {
        DecisionReview d = HandReviewer.judge(0, "A", "Alice", "RIVER",
                ActionType.CALL, 0.25 - HandReviewer.MARGIN + 0.001, 60, 20, 20);
        assertFalse(d.mistake());
    }

    // 이퀴티가 팟오즈에 크게 못 미치는 콜 = 실수, EV 손실 = (필요치-이퀴티)×(팟+콜).
    @Test
    void badCallIsMistakeWithEvLoss() {
        // 팟 1020, 콜 980 → 필요 0.49, 이퀴티 0.12 → 손실 = 0.37×2000 = 740칩 = 37bb
        DecisionReview d = HandReviewer.judge(3, "A", "Alice", "FLOP",
                ActionType.CALL, 0.12, 1020, 980, 20);
        assertTrue(d.mistake());
        assertEquals(37.0, d.evLossBb(), 0.001);
        assertEquals("CALL", d.action());
    }

    // 이퀴티가 팟오즈를 크게 상회하는데 폴드 = 실수(버린 콜 EV 만큼 손실).
    @Test
    void badFoldIsMistakeWithEvLoss() {
        // 팟 60, 콜 20 → 필요 0.25, 이퀴티 0.60 → 손실 = 0.35×80 = 28칩 = 1.4bb
        DecisionReview d = HandReviewer.judge(2, "B", "Bob", "TURN",
                ActionType.FOLD, 0.60, 60, 20, 20);
        assertTrue(d.mistake());
        assertEquals(1.4, d.evLossBb(), 0.001);
    }

    // 공짜 체크가 가능한데(toCall 0) 폴드 = 이퀴티만큼의 팟 지분을 그냥 버린 실수.
    @Test
    void foldingWhenCheckIsFreeIsMistake() {
        DecisionReview d = HandReviewer.judge(1, "B", "Bob", "FLOP",
                ActionType.FOLD, 0.30, 100, 0, 20);
        assertTrue(d.mistake());
        assertEquals(0.30 * 100 / 20, d.evLossBb(), 0.001);
        assertEquals(0.0, d.requiredEquity());
    }

    // ---------------------------------------------------------------- HandLog 통합 복기

    private static List<Card> cards(String... notations) {
        List<Card> list = new ArrayList<>();
        for (String s : notations) {
            list.add(Card.of(s));
        }
        return list;
    }

    /**
     * 대본 핸드: 히어로(A)가 72o 로 플랍 KQJ 에서 올인 콜(7하이, 이퀴티 바닥) →
     * 이 콜이 이 핸드의 최대 실수로 잡혀야 한다.
     */
    @Test
    void detectsWorstMistakeInScriptedHand() {
        Player a = new Player("A", "Alice", 1000);
        Player b = new Player("B", "Bob", 1000);
        // 딜 순서: SB(A) 7h, BB(B) Ah, A 2c, B Ad, 보드 Ks Qs Js 5d 9c
        Deck deck = Deck.ofOrder(cards("7h", "Ah", "2c", "Ad", "Ks", "Qs", "Js", "5d", "9c"));
        HandEngine engine = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        engine.start();
        engine.apply(Action.call("A"));        // 프리플랍 림프(팟오즈 좋아 실수 아님)
        engine.apply(Action.check("B"));
        engine.apply(Action.bet("B", 980));    // 플랍: B 올인
        engine.apply(Action.call("A"));        // A 가 7하이로 올인 콜 = 대실수
        assertTrue(engine.isComplete());

        HandLog log = engine.log();
        HandReview review = new HandReviewer(new EquityService())
                .review(log, 4000, new Random(42));

        assertNotNull(review.worstMistake());
        assertEquals("A", review.worstMistake().playerId());
        assertEquals("CALL", review.worstMistake().action());
        assertEquals("FLOP", review.worstMistake().street());
        assertEquals(3, review.worstMistake().step()); // 4번째 액션(인덱스 3)
        assertTrue(review.worstMistake().evLossBb() > 10,
                "7하이 올인 콜의 EV 손실은 커야 한다: " + review.worstMistake().evLossBb());
        assertTrue(review.totalEvLossBb() >= review.worstMistake().evLossBb());
    }

    // 실수 없는 핸드(프리플랍 림프-체크다운)는 worstMistake 가 null.
    @Test
    void cleanHandHasNoWorstMistake() {
        Player a = new Player("A", "Alice", 1000);
        Player b = new Player("B", "Bob", 1000);
        Deck deck = Deck.ofOrder(cards("As", "Kd", "Ah", "Kc", "2c", "7d", "9h", "Js", "3c"));
        HandEngine engine = new HandEngine(List.of(a, b), 0, 10, 20, deck);
        engine.start();
        engine.apply(Action.call("A")); // AA 림프: 팟오즈상 콜은 절대 실수 아님
        engine.apply(Action.check("B"));
        engine.apply(Action.check("B"));
        engine.apply(Action.check("A"));
        engine.apply(Action.check("B"));
        engine.apply(Action.check("A"));
        engine.apply(Action.check("B"));
        engine.apply(Action.check("A"));
        assertTrue(engine.isComplete());

        HandReview review = new HandReviewer(new EquityService())
                .review(engine.log(), 4000, new Random(7));

        assertNull(review.worstMistake());
        assertEquals(0.0, review.totalEvLossBb());
        // 판정 대상은 콜 1개(체크는 판정 안 함)
        assertEquals(1, review.decisions().size());
        assertFalse(review.decisions().get(0).mistake());
    }
}
