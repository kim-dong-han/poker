package com.homepoker.table;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.eval.HandCategory;
import com.homepoker.engine.eval.HandEvaluator;
import com.homepoker.engine.eval.HandRank;

import java.util.ArrayList;
import java.util.List;

/**
 * 좌석 뷰에 붙는 한국어 족보 라벨("투 페어 A·9" 등).
 * 홀카드가 공개된 좌석에만 계산하므로 상대 정보가 유출되지 않는다(리댁션은 호출부 책임).
 */
final class HandLabels {

    private HandLabels() {
    }

    /** 홀카드+보드가 5장 미만이면(프리플랍 등) null. */
    static String of(List<Card> hole, List<Card> board) {
        if (hole == null || hole.size() != 2 || board == null || hole.size() + board.size() < 5) {
            return null;
        }
        List<Card> all = new ArrayList<>(hole);
        all.addAll(board);
        return korean(HandEvaluator.evaluate(all));
    }

    static String korean(HandRank rank) {
        List<Integer> t = rank.tiebreakers();
        return switch (rank.category()) {
            case HIGH_CARD -> "하이카드 " + rankName(t.get(0));
            case ONE_PAIR -> "원 페어 " + rankName(t.get(0));
            case TWO_PAIR -> "투 페어 " + rankName(t.get(0)) + "·" + rankName(t.get(1));
            case THREE_OF_A_KIND -> "트리플 " + rankName(t.get(0));
            case STRAIGHT -> "스트레이트 (" + rankName(t.get(0)) + " 하이)";
            case FLUSH -> "플러시 (" + rankName(t.get(0)) + " 하이)";
            case FULL_HOUSE -> "풀하우스 " + rankName(t.get(0)) + "·" + rankName(t.get(1));
            case FOUR_OF_A_KIND -> "포카드 " + rankName(t.get(0));
            case STRAIGHT_FLUSH -> t.get(0) == 14 ? "로열 플러시"
                    : "스트레이트 플러시 (" + rankName(t.get(0)) + " 하이)";
        };
    }

    private static String rankName(int rank) {
        return switch (rank) {
            case 14 -> "A";
            case 13 -> "K";
            case 12 -> "Q";
            case 11 -> "J";
            default -> String.valueOf(rank);
        };
    }
}
