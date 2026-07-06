package com.homepoker.engine.eval;

import com.homepoker.engine.card.Card;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandEvaluatorTest {

    private static HandRank rank(String... cards) {
        List<Card> list = Arrays.stream(cards).map(Card::of).toList();
        return HandEvaluator.evaluate(list);
    }

    @Test
    void classifiesEachCategory() {
        assertEquals(HandCategory.STRAIGHT_FLUSH, rank("Ts", "Js", "Qs", "Ks", "As").category());
        assertEquals(HandCategory.FOUR_OF_A_KIND, rank("9c", "9d", "9h", "9s", "2c").category());
        assertEquals(HandCategory.FULL_HOUSE, rank("Kc", "Kd", "Kh", "2s", "2c").category());
        assertEquals(HandCategory.FLUSH, rank("2h", "5h", "9h", "Jh", "Kh").category());
        assertEquals(HandCategory.STRAIGHT, rank("5c", "6d", "7h", "8s", "9c").category());
        assertEquals(HandCategory.THREE_OF_A_KIND, rank("7c", "7d", "7h", "2s", "9c").category());
        assertEquals(HandCategory.TWO_PAIR, rank("Ac", "Ad", "Kh", "Ks", "2c").category());
        assertEquals(HandCategory.ONE_PAIR, rank("Ac", "Ad", "Kh", "Qs", "2c").category());
        assertEquals(HandCategory.HIGH_CARD, rank("Ac", "Jd", "9h", "5s", "2c").category());
    }

    @Test
    void wheelIsTheLowestStraight() {
        HandRank wheel = rank("Ac", "2d", "3h", "4s", "5c");
        assertEquals(HandCategory.STRAIGHT, wheel.category());
        assertEquals(List.of(5), wheel.tiebreakers()); // 탑은 A 가 아니라 5

        HandRank sixHigh = rank("2c", "3d", "4h", "5s", "6c");
        assertTrue(sixHigh.compareTo(wheel) > 0); // 6-high 스트레이트 > 휠
    }

    @Test
    void wheelStraightFlushBeatsNothingHigher() {
        HandRank wheelSF = rank("As", "2s", "3s", "4s", "5s");
        assertEquals(HandCategory.STRAIGHT_FLUSH, wheelSF.category());
        assertEquals(List.of(5), wheelSF.tiebreakers());
    }

    @Test
    void higherCategoryAlwaysWins() {
        assertTrue(rank("2c", "2d", "2h", "2s", "3c") // 쿼드
                .compareTo(rank("Ac", "Ad", "Ah", "Ks", "Kc")) > 0); // 풀하우스
        assertTrue(rank("2h", "3h", "4h", "5h", "7h") // 플러시
                .compareTo(rank("5c", "6d", "7h", "8s", "9c")) > 0); // 스트레이트
    }

    @Test
    void kickersBreakTiesWithinCategory() {
        HandRank aceKingKicker = rank("Ac", "Ad", "Kh", "Qs", "Jc");
        HandRank aceQueenKicker = rank("Ah", "As", "Qh", "Jd", "9c");
        assertTrue(aceKingKicker.compareTo(aceQueenKicker) > 0);

        // 동일 투페어, 키커만 다름
        HandRank kickerNine = rank("Ac", "Ad", "Kh", "Ks", "9c");
        HandRank kickerTwo = rank("Ah", "As", "Kc", "Kd", "2h");
        assertTrue(kickerNine.compareTo(kickerTwo) > 0);
    }

    @Test
    void identicalHandsTie() {
        assertEquals(0, rank("Ac", "Ad", "Kh", "Qs", "Jc")
                .compareTo(rank("Ah", "As", "Kd", "Qc", "Jh")));
    }

    @Test
    void picksBestFiveFromSeven() {
        // 홀 As Ks + 보드 Qs Js Ts 2c 3d  → 로열(A-high 스트레이트 플러시)
        HandRank royal = rank("As", "Ks", "Qs", "Js", "Ts", "2c", "3d");
        assertEquals(HandCategory.STRAIGHT_FLUSH, royal.category());
        assertEquals(List.of(14), royal.tiebreakers());
    }

    @Test
    void sevenCardFindsHiddenTwoPair() {
        HandRank r = rank("Ac", "Ad", "Kh", "Ks", "Qc", "7d", "2s");
        assertEquals(HandCategory.TWO_PAIR, r.category());
        assertEquals(List.of(14, 13, 12), r.tiebreakers()); // AA KK + Q 키커
    }
}
