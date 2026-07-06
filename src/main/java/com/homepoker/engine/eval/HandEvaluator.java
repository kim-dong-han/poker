package com.homepoker.engine.eval;

import com.homepoker.engine.card.Card;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 5~7장 카드로부터 최강 5장 족보를 계산한다.
 * 7장(홀2+보드5)이면 C(7,5)=21 조합을 모두 평가해 최댓값을 취한다 —
 * 명료함 우선. 몬테카를로 이퀴티처럼 수만 번 호출이 필요해지면 나중에 룩업 기반으로 교체 가능.
 */
public final class HandEvaluator {

    private HandEvaluator() {
    }

    /** 5~7장에서 최강 5장 핸드를 평가한다. */
    public static HandRank evaluate(Collection<Card> cards) {
        List<Card> list = new ArrayList<>(cards);
        if (list.size() < 5 || list.size() > 7) {
            throw new IllegalArgumentException("5~7장이어야 한다. 입력: " + list.size());
        }
        if (list.size() == 5) {
            return evaluateFive(list);
        }
        HandRank best = null;
        int[][] combos = combinations(list.size());
        for (int[] combo : combos) {
            List<Card> five = List.of(
                    list.get(combo[0]), list.get(combo[1]), list.get(combo[2]),
                    list.get(combo[3]), list.get(combo[4]));
            HandRank rank = evaluateFive(five);
            if (best == null || rank.compareTo(best) > 0) {
                best = rank;
            }
        }
        return best;
    }

    /** 정확히 5장을 족보로 평가한다. */
    static HandRank evaluateFive(List<Card> five) {
        if (five.size() != 5) {
            throw new IllegalArgumentException("정확히 5장이어야 한다");
        }

        boolean flush = five.stream().map(Card::suit).distinct().count() == 1;

        // 숫자값 -> 개수 (TreeMap 으로 값 순 정렬 보장)
        Map<Integer, Integer> counts = new TreeMap<>();
        for (Card card : five) {
            counts.merge(card.rank().value(), 1, Integer::sum);
        }

        int straightHigh = straightHigh(counts.keySet());

        // (개수 내림 → 값 내림) 순으로 정렬된 숫자값들. 그룹 계열 족보의 tiebreaker 순서가 된다.
        List<Integer> ordered = counts.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Integer, Integer> e) -> e.getValue()).reversed()
                        .thenComparing(Comparator.comparingInt((Map.Entry<Integer, Integer> e) -> e.getKey()).reversed()))
                .map(Map.Entry::getKey)
                .toList();

        List<Integer> countPattern = counts.values().stream()
                .sorted(Comparator.reverseOrder())
                .toList();

        if (flush && straightHigh > 0) {
            return new HandRank(HandCategory.STRAIGHT_FLUSH, List.of(straightHigh));
        }
        if (countPattern.equals(List.of(4, 1))) {
            return new HandRank(HandCategory.FOUR_OF_A_KIND, ordered);
        }
        if (countPattern.equals(List.of(3, 2))) {
            return new HandRank(HandCategory.FULL_HOUSE, ordered);
        }
        if (flush) {
            return new HandRank(HandCategory.FLUSH, ordered);
        }
        if (straightHigh > 0) {
            return new HandRank(HandCategory.STRAIGHT, List.of(straightHigh));
        }
        if (countPattern.equals(List.of(3, 1, 1))) {
            return new HandRank(HandCategory.THREE_OF_A_KIND, ordered);
        }
        if (countPattern.equals(List.of(2, 2, 1))) {
            return new HandRank(HandCategory.TWO_PAIR, ordered);
        }
        if (countPattern.equals(List.of(2, 1, 1, 1))) {
            return new HandRank(HandCategory.ONE_PAIR, ordered);
        }
        return new HandRank(HandCategory.HIGH_CARD, ordered);
    }

    /**
     * 5개의 서로 다른 숫자값이 스트레이트를 이루면 그 탑 카드 값을, 아니면 0 을 반환한다.
     * 휠(A-2-3-4-5)은 에이스를 낮은 카드로 취급하여 탑을 5 로 본다.
     */
    private static int straightHigh(Collection<Integer> distinctValues) {
        if (distinctValues.size() != 5) {
            return 0;
        }
        List<Integer> values = distinctValues.stream().sorted().toList(); // 오름차순
        // 휠: {2,3,4,5,14}
        if (values.equals(List.of(2, 3, 4, 5, 14))) {
            return 5;
        }
        int min = values.get(0);
        int max = values.get(4);
        if (max - min == 4) { // 5개 distinct + 폭 4 = 연속
            return max;
        }
        return 0;
    }

    /** n(6 또는 7)개 중 5개를 고르는 인덱스 조합. */
    private static int[][] combinations(int n) {
        List<int[]> result = new ArrayList<>();
        int[] c = new int[5];
        for (c[0] = 0; c[0] < n; c[0]++) {
            for (c[1] = c[0] + 1; c[1] < n; c[1]++) {
                for (c[2] = c[1] + 1; c[2] < n; c[2]++) {
                    for (c[3] = c[2] + 1; c[3] < n; c[3]++) {
                        for (c[4] = c[3] + 1; c[4] < n; c[4]++) {
                            result.add(new int[]{c[0], c[1], c[2], c[3], c[4]});
                        }
                    }
                }
            }
        }
        return result.toArray(new int[0][]);
    }
}
