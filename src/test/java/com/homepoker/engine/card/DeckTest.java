package com.homepoker.engine.card;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeckTest {

    @Test
    void standardDeckHas52UniqueCards() {
        Deck deck = Deck.standard();
        assertEquals(52, deck.remaining());
        Set<Card> seen = new HashSet<>();
        while (deck.remaining() > 0) {
            seen.add(deck.deal());
        }
        assertEquals(52, seen.size());
    }

    @Test
    void shuffleWithSameSeedIsReproducible() {
        Deck a = Deck.shuffled(new Random(42));
        Deck b = Deck.shuffled(new Random(42));
        for (int i = 0; i < 52; i++) {
            assertEquals(a.deal(), b.deal());
        }
    }

    @Test
    void dealingBeyondEmptyThrows() {
        Deck deck = Deck.standard();
        for (int i = 0; i < 52; i++) {
            deck.deal();
        }
        assertThrows(IllegalStateException.class, deck::deal);
    }
}
