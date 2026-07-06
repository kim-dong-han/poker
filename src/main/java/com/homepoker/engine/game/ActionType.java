package com.homepoker.engine.game;

/**
 * 플레이어가 취할 수 있는 액션. 블라인드 포스팅은 엔진이 내부에서 처리하므로 액션이 아니다.
 */
public enum ActionType {
    FOLD,
    CHECK,
    CALL,
    /** 이번 베팅 라운드에 아직 벳이 없을 때 처음 칩을 거는 것. amount = 거는 총액. */
    BET,
    /** 이미 벳이 있을 때 더 올리는 것. amount = 올린 뒤의 총 커밋액("raise to"). */
    RAISE
}
