package com.homepoker.engine.game;

import java.util.Objects;

/**
 * 불변 액션 명령. amount 의 의미는 타입에 따라 다르다:
 *  - BET/RAISE : 이번 스트리트에 넣게 될 "총" 커밋액(raise-to 방식)
 *  - CALL/CHECK/FOLD : 무시(0)
 */
public record Action(String playerId, ActionType type, long amount) {

    public Action {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        if (amount < 0) {
            throw new IllegalArgumentException("amount 는 음수일 수 없다");
        }
    }

    public static Action fold(String playerId) {
        return new Action(playerId, ActionType.FOLD, 0);
    }

    public static Action check(String playerId) {
        return new Action(playerId, ActionType.CHECK, 0);
    }

    public static Action call(String playerId) {
        return new Action(playerId, ActionType.CALL, 0);
    }

    public static Action bet(String playerId, long amount) {
        return new Action(playerId, ActionType.BET, amount);
    }

    public static Action raiseTo(String playerId, long amount) {
        return new Action(playerId, ActionType.RAISE, amount);
    }
}
