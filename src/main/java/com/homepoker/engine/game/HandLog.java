package com.homepoker.engine.game;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.card.Deck;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 한 판의 이벤트 소싱 기록. "초기 조건(좌석·버튼·블라인드·덱 순서) + 적용된 액션 리스트"만
 * 담으면 순수 엔진을 그대로 다시 돌려 임의 시점의 상태를 결정적으로 복원할 수 있다.
 *
 * 순수 도메인 — WebSocket/DB 를 전혀 모른다. HandEngine 이 진행 중 스스로 채워 {@link HandEngine#log()}로 낸다.
 */
public record HandLog(
        List<Seat> seats,
        int button,
        long smallBlind,
        long bigBlind,
        List<Card> deckOrder,
        List<Action> actions) {

    /** 핸드 시작 시점(블라인드 차감 전)의 한 좌석 스냅샷. */
    public record Seat(String id, String name, long startStack) {}

    public HandLog {
        seats = List.copyOf(seats);
        deckOrder = List.copyOf(deckOrder);
        actions = List.copyOf(actions);
        Objects.requireNonNull(seats);
    }

    public int actionCount() {
        return actions.size();
    }

    /**
     * 앞에서부터 {@code steps}개의 액션을 적용한 시점의 엔진을 새로 복원한다.
     * {@code steps == 0}이면 딜·블라인드 직후(첫 액션 전) 상태.
     */
    public HandEngine stateAt(int steps) {
        if (steps < 0 || steps > actions.size()) {
            throw new IllegalArgumentException("steps 범위 오류: " + steps + " (0.." + actions.size() + ")");
        }
        List<Player> players = new ArrayList<>(seats.size());
        for (Seat s : seats) {
            players.add(new Player(s.id(), s.name(), s.startStack()));
        }
        HandEngine engine = new HandEngine(players, button, smallBlind, bigBlind, Deck.ofOrder(deckOrder));
        engine.start();
        for (int i = 0; i < steps; i++) {
            engine.apply(actions.get(i));
        }
        return engine;
    }

    /** 모든 액션을 적용한 최종 상태(= 원래 핸드의 종료 상태와 동일). */
    public HandEngine finalState() {
        return stateAt(actions.size());
    }
}
