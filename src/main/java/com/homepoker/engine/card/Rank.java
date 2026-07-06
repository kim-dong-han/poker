package com.homepoker.engine.card;

/**
 * 카드 숫자. value 는 하이카드/스트레이트 비교용(2=2 ... A=14).
 * 에이스는 스트레이트에서 A-2-3-4-5(휠)로도 쓰이며, 그 처리는 HandEvaluator 가 담당한다.
 */
public enum Rank {
    TWO(2, '2'),
    THREE(3, '3'),
    FOUR(4, '4'),
    FIVE(5, '5'),
    SIX(6, '6'),
    SEVEN(7, '7'),
    EIGHT(8, '8'),
    NINE(9, '9'),
    TEN(10, 'T'),
    JACK(11, 'J'),
    QUEEN(12, 'Q'),
    KING(13, 'K'),
    ACE(14, 'A');

    private final int value;
    private final char symbol;

    Rank(int value, char symbol) {
        this.value = value;
        this.symbol = symbol;
    }

    public int value() {
        return value;
    }

    public char symbol() {
        return symbol;
    }

    public static Rank fromSymbol(char symbol) {
        char upper = Character.toUpperCase(symbol);
        for (Rank rank : values()) {
            if (rank.symbol == upper) {
                return rank;
            }
        }
        throw new IllegalArgumentException("알 수 없는 숫자 기호: " + symbol);
    }
}
