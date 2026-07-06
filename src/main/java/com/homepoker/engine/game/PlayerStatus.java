package com.homepoker.engine.game;

/**
 * 현재 핸드 안에서의 플레이어 상태.
 * ACTIVE  : 아직 액션할 수 있음(칩 보유, 폴드/올인 아님)
 * FOLDED  : 이번 핸드 포기
 * ALL_IN  : 칩을 전부 넣어 더 액션할 수 없음(쇼다운에는 참가)
 */
public enum PlayerStatus {
    ACTIVE,
    FOLDED,
    ALL_IN
}
