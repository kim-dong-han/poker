package com.homepoker.engine.card;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 52장 덱. 셔플 시드를 주입할 수 있어(테스트 재현성) 결정적 테스트가 가능하다.
 * 딜은 위(top)에서 한 장씩 뽑는다.
 */
public final class Deck {

    private final Deque<Card> cards = new ArrayDeque<>(52);

    private Deck() {
    }

    /** 셔플하지 않은 정렬된 52장 덱. */
    public static Deck standard() {
        Deck deck = new Deck();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.cards.add(new Card(rank, suit));
            }
        }
        return deck;
    }

    /** 주어진 Random 으로 셔플된 덱(테스트 재현성을 위해 시드 주입 가능). */
    public static Deck shuffled(Random random) {
        Deck deck = standard();
        List<Card> list = new ArrayList<>(deck.cards);
        Collections.shuffle(list, random);
        deck.cards.clear();
        deck.cards.addAll(list);
        return deck;
    }

    public static Deck shuffled() {
        return shuffled(new Random());
    }

    /**
     * 지정한 순서로 카드를 쌓은 덱(맨 앞 = 가장 먼저 딜). 테스트에서 결정적 시나리오를 만들 때 사용.
     * 52장 미만이어도 되지만 카드가 중복돼선 안 된다.
     */
    public static Deck ofOrder(List<Card> order) {
        if (order.size() != new java.util.HashSet<>(order).size()) {
            throw new IllegalArgumentException("덱에 중복 카드가 있다");
        }
        Deck deck = new Deck();
        deck.cards.addAll(order);
        return deck;
    }

    /** 위에서 한 장 뽑는다. */
    public Card deal() {
        Card card = cards.pollFirst();
        if (card == null) {
            throw new IllegalStateException("덱이 비었다");
        }
        return card;
    }

    public int remaining() {
        return cards.size();
    }

    /** 아직 딜하지 않은 카드를 딜 순서대로(맨 앞 = 다음에 뽑힐 카드) 스냅샷한다. 이벤트 소싱 기록용. */
    public List<Card> remainingInOrder() {
        return List.copyOf(cards);
    }
}
