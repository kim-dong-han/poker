package com.homepoker.table;

import com.homepoker.engine.card.Deck;
import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 하나의 홈게임 테이블. 좌석·버튼·블라인드를 관리하고 매 핸드마다 HandEngine 을 생성한다.
 *
 * 동시성: 모든 상태 변경 메서드를 synchronized 로 직렬화한다. 이렇게 하면 순수 도메인인
 * HandEngine 은 절대 동시 접근을 받지 않으므로 "테이블당 단일 라이터" 모델이 성립한다
 * (계획서의 '테이블당 단일 스레드 액션 큐'와 동일한 안전성 — 락으로 단순 구현).
 */
public class Table {

    private final String id;
    private final long smallBlind;
    private final long bigBlind;

    /** 착석 순서 = 좌석 순서. */
    private final Map<String, Player> seats = new LinkedHashMap<>();

    private HandEngine engine;   // 진행 중 핸드(없으면 null 또는 종료 상태)
    private int handCount = 0;

    public Table(String id, long smallBlind, long bigBlind) {
        this.id = id;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
    }

    public String id() {
        return id;
    }

    public long smallBlind() {
        return smallBlind;
    }

    public long bigBlind() {
        return bigBlind;
    }

    public synchronized void seat(String playerId, String name, long buyIn) {
        if (seats.containsKey(playerId)) {
            // 이미 앉아 있으면 재바이인은 RuleGuard(3단계)에서 다룬다. 지금은 무시.
            return;
        }
        seats.put(playerId, new Player(playerId, name, buyIn));
    }

    /** 새 핸드 시작. 칩이 있는 좌석이 2명 이상이어야 한다. */
    public synchronized void startHand() {
        List<Player> live = new ArrayList<>();
        for (Player p : seats.values()) {
            if (p.stack() > 0) {
                live.add(p);
            }
        }
        if (live.size() < 2) {
            throw new IllegalStateException("핸드를 시작하려면 칩 보유 플레이어가 2명 이상이어야 한다");
        }
        int button = handCount % live.size();
        engine = new HandEngine(live, button, smallBlind, bigBlind, Deck.shuffled());
        engine.start();
        handCount++;
    }

    public synchronized void applyAction(String playerId, String type, long amount) {
        if (engine == null) {
            throw new IllegalStateException("진행 중인 핸드가 없다");
        }
        ActionType actionType = ActionType.valueOf(type.toUpperCase());
        Action action = switch (actionType) {
            case FOLD -> Action.fold(playerId);
            case CHECK -> Action.check(playerId);
            case CALL -> Action.call(playerId);
            case BET -> Action.bet(playerId, amount);
            case RAISE -> Action.raiseTo(playerId, amount);
        };
        engine.apply(action);
    }

    public synchronized HandEngine engine() {
        return engine;
    }

    public synchronized List<Player> seatedPlayers() {
        return new ArrayList<>(seats.values());
    }

    public synchronized List<String> seatedPlayerIds() {
        return new ArrayList<>(seats.keySet());
    }
}
