package com.homepoker.engine.card;

import java.util.Objects;

/**
 * 불변 카드. "As", "Th", "2c" 같은 2글자 표기와 상호 변환된다.
 */
public record Card(Rank rank, Suit suit) implements Comparable<Card> {

    public Card {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(suit, "suit");
    }

    /** "As", "Th", "2c" 형태의 2글자 표기를 파싱한다. */
    public static Card of(String notation) {
        Objects.requireNonNull(notation, "notation");
        if (notation.length() != 2) {
            throw new IllegalArgumentException("카드 표기는 2글자여야 한다: " + notation);
        }
        return new Card(Rank.fromSymbol(notation.charAt(0)), Suit.fromSymbol(notation.charAt(1)));
    }

    public static Card of(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }

    /** 숫자 우열만으로 비교(무늬 무시). 스트레이트/하이카드 정렬에 사용. */
    @Override
    public int compareTo(Card other) {
        return Integer.compare(this.rank.value(), other.rank.value());
    }

    @Override
    public String toString() {
        return "" + rank.symbol() + suit.symbol();
    }
}
