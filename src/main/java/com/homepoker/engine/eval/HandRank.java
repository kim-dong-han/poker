package com.homepoker.engine.eval;

import java.util.List;
import java.util.Objects;

/**
 * 평가된 핸드의 강도. 두 핸드의 우열은
 * ①족보(category) 비교 → 동률이면 ②동점 처리 값(tiebreakers) 사전식 비교로 결정된다.
 *
 * tiebreakers 는 "센 것부터" 나열한 숫자 값 리스트다. 예)
 *  - 원페어 AA + K,Q,J 키커  → [14, 13, 12, 11]
 *  - 풀하우스 KKK + 22       → [13, 2]
 *  - 휠 스트레이트 A2345      → [5]  (에이스는 낮은 5로 취급)
 */
public record HandRank(HandCategory category, List<Integer> tiebreakers) implements Comparable<HandRank> {

    public HandRank {
        Objects.requireNonNull(category, "category");
        tiebreakers = List.copyOf(tiebreakers);
    }

    @Override
    public int compareTo(HandRank other) {
        int byCategory = this.category.compareTo(other.category);
        if (byCategory != 0) {
            return byCategory;
        }
        int n = Math.min(this.tiebreakers.size(), other.tiebreakers.size());
        for (int i = 0; i < n; i++) {
            int cmp = Integer.compare(this.tiebreakers.get(i), other.tiebreakers.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return category + tiebreakers.toString();
    }
}
