package com.homepoker.table;

import com.homepoker.engine.card.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HandLabelsTest {

    private static List<Card> cards(String... codes) {
        return java.util.Arrays.stream(codes).map(Card::of).toList();
    }

    @Test
    void 투페어_라벨은_높은_페어부터_표기한다() {
        String label = HandLabels.of(cards("Ah", "9c"), cards("Ad", "9s", "2h"));
        assertEquals("투 페어 A·9", label);
    }

    @Test
    void 플러시와_스트레이트는_하이카드를_병기한다() {
        assertEquals("플러시 (A 하이)",
                HandLabels.of(cards("Ah", "7h"), cards("Kh", "4h", "2h")));
        assertEquals("스트레이트 (J 하이)",
                HandLabels.of(cards("Jd", "Tc"), cards("9h", "8s", "7d")));
    }

    @Test
    void 로열플러시는_별도_라벨() {
        assertEquals("로열 플러시",
                HandLabels.of(cards("Ah", "Kh"), cards("Qh", "Jh", "Th")));
        assertEquals("스트레이트 플러시 (9 하이)",
                HandLabels.of(cards("9h", "8h"), cards("7h", "6h", "5h")));
    }

    @Test
    void 프리플랍이나_홀카드_미공개면_null() {
        assertNull(HandLabels.of(cards("Ah", "Kh"), List.of()));
        assertNull(HandLabels.of(null, cards("Qh", "Jh", "Th")));
    }
}
