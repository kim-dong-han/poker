package com.homepoker.engine.card;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardTest {

    @Test
    void parsesTwoCharacterNotation() {
        Card ace = Card.of("As");
        assertEquals(Rank.ACE, ace.rank());
        assertEquals(Suit.SPADES, ace.suit());
        assertEquals(Rank.TEN, Card.of("Th").rank());
        assertEquals(Suit.CLUBS, Card.of("2c").suit());
    }

    @Test
    void roundTripsThroughToString() {
        Card card = Card.of("Kd");
        assertEquals("Kd", card.toString());
        assertEquals(card, Card.of(card.toString()));
    }

    @Test
    void comparesByRankValueIgnoringSuit() {
        assertTrue(Card.of("As").compareTo(Card.of("Kh")) > 0);
        assertEquals(0, Card.of("7c").compareTo(Card.of("7d")));
    }

    @Test
    void rejectsBadNotation() {
        assertThrows(IllegalArgumentException.class, () -> Card.of("Zx"));
        assertThrows(IllegalArgumentException.class, () -> Card.of("A"));
    }
}
