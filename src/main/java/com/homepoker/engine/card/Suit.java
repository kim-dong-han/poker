package com.homepoker.engine.card;

/**
 * 카드 무늬. 홀덤에서 무늬 간 우열은 없다(플러시 판정에만 사용).
 */
public enum Suit {
    CLUBS('c'),
    DIAMONDS('d'),
    HEARTS('h'),
    SPADES('s');

    private final char symbol;

    Suit(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return symbol;
    }

    public static Suit fromSymbol(char symbol) {
        for (Suit suit : values()) {
            if (suit.symbol == Character.toLowerCase(symbol)) {
                return suit;
            }
        }
        throw new IllegalArgumentException("알 수 없는 무늬 기호: " + symbol);
    }
}
