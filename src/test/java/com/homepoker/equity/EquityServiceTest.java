package com.homepoker.equity;

import com.homepoker.engine.card.Card;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquityServiceTest {

    private final EquityService equity = new EquityService();

    private static List<Card> cards(String... cs) {
        return java.util.Arrays.stream(cs).map(Card::of).toList();
    }

    // ---- 결정적: 넛 락(로열)은 상대가 절대 이기거나 비길 수 없다 → 이퀴티 100% ----
    @Test
    void nutLockHasFullEquity() {
        // 히어로 로열(A K Q J T 스페이드), 보드 완성. 상대는 A/K 스페이드를 가질 수 없다.
        Equity e = equity.estimate(
                cards("As", "Ks"),
                cards("Qs", "Js", "Ts", "2c", "3d"),
                1, 200, new Random(1));
        assertEquals(1.0, e.win(), 1e-9);
        assertEquals(0.0, e.tie(), 1e-9);
        assertEquals(1.0, e.equity(), 1e-9);
    }

    // ---- 결정적: 보드 자체가 로열 → 누구도 못 이기고 항상 찹 ----
    @Test
    void boardRoyalForcesChop() {
        Equity headsUp = equity.estimate(
                cards("2c", "3d"),
                cards("Ah", "Kh", "Qh", "Jh", "Th"),
                1, 200, new Random(2));
        assertEquals(0.0, headsUp.win(), 1e-9);
        assertEquals(1.0, headsUp.tie(), 1e-9);
        assertEquals(0.5, headsUp.equity(), 1e-9); // 2인 찹 → 지분 1/2

        Equity threeWay = equity.estimate(
                cards("2c", "3d"),
                cards("Ah", "Kh", "Qh", "Jh", "Th"),
                2, 200, new Random(3));
        assertEquals(1.0 / 3, threeWay.equity(), 1e-9); // 3인 찹 → 지분 1/3
    }

    // ---- 확률 정합성: AA 프리플랍 vs 1명 ≈ 85% ----
    @Test
    void pocketAcesAreAboutEightyFivePercentHeadsUp() {
        Equity e = equity.estimate(cards("As", "Ah"), List.of(), 1, 20000, new Random(42));
        assertTrue(e.equity() > 0.80 && e.equity() < 0.90,
                "AA 헤즈업 이퀴티 예상 밖: " + e.equity());
    }

    // ---- 상대가 많을수록 이퀴티는 내려간다 ----
    @Test
    void moreOpponentsLowerEquity() {
        Equity vs1 = equity.estimate(cards("As", "Ah"), List.of(), 1, 20000, new Random(7));
        Equity vs4 = equity.estimate(cards("As", "Ah"), List.of(), 4, 20000, new Random(7));
        assertTrue(vs1.equity() > vs4.equity(),
                "vs1=" + vs1.equity() + " vs4=" + vs4.equity());
    }

    // ---- 최악 핸드(72o)는 무작위 상대에게도 절반 미만 ----
    @Test
    void worstHandIsUnderfifty() {
        Equity e = equity.estimate(cards("7d", "2c"), List.of(), 1, 20000, new Random(9));
        assertTrue(e.equity() < 0.45, "72o 이퀴티 예상 밖: " + e.equity());
    }

    // ---- 재현성: 같은 시드 → 같은 결과 ----
    @Test
    void sameSeedIsReproducible() {
        Equity a = equity.estimate(cards("Kd", "Kc"), cards("2h", "7s", "Tc"), 2, 5000, new Random(123));
        Equity b = equity.estimate(cards("Kd", "Kc"), cards("2h", "7s", "Tc"), 2, 5000, new Random(123));
        assertEquals(a.equity(), b.equity(), 1e-12);
    }
}
