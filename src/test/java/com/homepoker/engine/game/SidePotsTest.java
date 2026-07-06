package com.homepoker.engine.game;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SidePotsTest {

    @Test
    void singlePotWhenEqualContributions() {
        Map<String, Long> c = new LinkedHashMap<>();
        c.put("A", 50L);
        c.put("B", 50L);
        List<Pot> pots = SidePots.build(c, Set.of());
        assertEquals(1, pots.size());
        assertEquals(100L, pots.get(0).amount());
        assertEquals(List.of("A", "B"), pots.get(0).eligiblePlayerIds());
    }

    @Test
    void buildsMainAndSidePotForShortAllIn() {
        // A=100, B=100, C=40(올인) → 메인 120(A,B,C), 사이드 120(A,B)
        Map<String, Long> c = new LinkedHashMap<>();
        c.put("A", 100L);
        c.put("B", 100L);
        c.put("C", 40L);
        List<Pot> pots = SidePots.build(c, Set.of());
        assertEquals(2, pots.size());
        assertEquals(120L, pots.get(0).amount());
        assertEquals(List.of("A", "B", "C"), pots.get(0).eligiblePlayerIds());
        assertEquals(120L, pots.get(1).amount());
        assertEquals(List.of("A", "B"), pots.get(1).eligiblePlayerIds());
    }

    @Test
    void foldedPlayerContributesChipsButIsNotEligible() {
        // C가 40 넣고 폴드 → 그 40도 메인팟에 들어가되 C는 자격 없음
        Map<String, Long> c = new LinkedHashMap<>();
        c.put("A", 100L);
        c.put("B", 100L);
        c.put("C", 40L);
        List<Pot> pots = SidePots.build(c, Set.of("C"));
        assertEquals(120L, pots.get(0).amount());
        assertEquals(List.of("A", "B"), pots.get(0).eligiblePlayerIds());
    }

    @Test
    void conservesTotalChips() {
        Map<String, Long> c = new LinkedHashMap<>();
        c.put("A", 30L);
        c.put("B", 70L);
        c.put("C", 100L);
        long sum = SidePots.build(c, Set.of()).stream().mapToLong(Pot::amount).sum();
        assertEquals(200L, sum);
    }
}
