package com.homepoker.range;

import com.homepoker.engine.card.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreflopRangeTest {

    private final PreflopRangeService service = new PreflopRangeService();

    // ---- 홀카드 → 표준 표기 ----
    @Test
    void notationForPairSuitedOffsuit() {
        assertEquals("AA", HandNotation.of(List.of(Card.of("As"), Card.of("Ah"))));
        assertEquals("AKs", HandNotation.of(List.of(Card.of("Ah"), Card.of("Kh")))); // 같은 무늬
        assertEquals("AKo", HandNotation.of(List.of(Card.of("Ah"), Card.of("Kd")))); // 다른 무늬
        assertEquals("AJs", HandNotation.of(List.of(Card.of("Js"), Card.of("As")))); // 높은 숫자 앞
    }

    // ---- 레인지가 로드되고 포지션마다 넓어진다 ----
    @Test
    void rangesLoadAndWidenByPosition() {
        int utg = service.openingRange(Position.UTG).size();
        int co = service.openingRange(Position.CO).size();
        int btn = service.openingRange(Position.BTN).size();
        assertTrue(utg > 0);
        assertTrue(co > utg, "CO 는 UTG 보다 넓어야 함");
        assertTrue(btn > co, "BTN 은 CO 보다 넓어야 함");
        assertTrue(service.openingRange(Position.BB).isEmpty(), "BB 는 오픈 레인지 없음");
    }

    // ---- 오픈 판정 ----
    @Test
    void shouldOpenMembership() {
        // AA 는 어느 포지션에서나 오픈
        assertTrue(service.shouldOpen(Position.UTG, "AA"));
        // 72o 는 어디서도 오픈 아님
        assertFalse(service.shouldOpen(Position.BTN, "72o"));
        // 약한 오프수트 에이스는 UTG 에선 접고 BTN 에선 연다
        assertFalse(service.shouldOpen(Position.UTG, "A5o"));
        assertTrue(service.shouldOpen(Position.BTN, "A5o"));
    }

    @Test
    void shouldOpenFromHoleCards() {
        List<Card> aces = List.of(Card.of("As"), Card.of("Ac"));
        assertTrue(service.shouldOpen(Position.UTG, aces));
        List<Card> trash = List.of(Card.of("7h"), Card.of("2d"));
        assertFalse(service.shouldOpen(Position.BTN, trash));
    }
}
