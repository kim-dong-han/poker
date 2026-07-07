package com.homepoker.range;

import com.homepoker.engine.card.Card;

import java.util.List;

/**
 * 홀카드 2장을 표준 프리플랍 표기로 바꾼다: 페어는 "AA", 수딧은 "AKs", 오프수트는 "AKo".
 * 높은 숫자가 앞에 온다. 레인지 조회(오버레이)에 쓰는 순수 유틸.
 */
public final class HandNotation {

    private HandNotation() {
    }

    public static String of(Card a, Card b) {
        char hi = a.rank().value() >= b.rank().value() ? a.rank().symbol() : b.rank().symbol();
        char lo = a.rank().value() >= b.rank().value() ? b.rank().symbol() : a.rank().symbol();
        if (a.rank() == b.rank()) {
            return "" + hi + lo; // 페어: 접미사 없음
        }
        String suffix = a.suit() == b.suit() ? "s" : "o";
        return "" + hi + lo + suffix;
    }

    public static String of(List<Card> hole) {
        if (hole.size() != 2) {
            throw new IllegalArgumentException("홀카드 2장이 필요하다: " + hole);
        }
        return of(hole.get(0), hole.get(1));
    }
}
