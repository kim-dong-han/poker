package com.homepoker.engine.game;

import com.homepoker.engine.card.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 테이블의 한 플레이어. 스택/상태/홀카드는 핸드 진행에 따라 변한다.
 * 순수 도메인 객체 — WebSocket/DB 를 전혀 모른다.
 */
public class Player {

    private final String id;
    private final String name;
    private long stack;

    private final List<Card> holeCards = new ArrayList<>(2);
    private PlayerStatus status = PlayerStatus.ACTIVE;

    public Player(String id, String name, long stack) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        if (stack < 0) {
            throw new IllegalArgumentException("스택은 음수일 수 없다");
        }
        this.stack = stack;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long stack() {
        return stack;
    }

    public PlayerStatus status() {
        return status;
    }

    public List<Card> holeCards() {
        return List.copyOf(holeCards);
    }

    // --- 엔진 전용 상태 변경 (package-private) ---

    void resetForNewHand() {
        holeCards.clear();
        status = PlayerStatus.ACTIVE;
    }

    void dealHole(Card card) {
        if (holeCards.size() >= 2) {
            throw new IllegalStateException("홀카드는 2장까지");
        }
        holeCards.add(card);
    }

    /** 스택에서 최대 amount 만큼 빼서 실제로 뺀 금액을 돌려준다(부족하면 있는 만큼 = 올인). */
    long removeFromStack(long amount) {
        long taken = Math.min(amount, stack);
        stack -= taken;
        if (stack == 0 && status == PlayerStatus.ACTIVE) {
            status = PlayerStatus.ALL_IN;
        }
        return taken;
    }

    void addToStack(long amount) {
        stack += amount;
    }

    void fold() {
        status = PlayerStatus.FOLDED;
    }

    @Override
    public String toString() {
        return name + "(" + stack + ")";
    }
}
