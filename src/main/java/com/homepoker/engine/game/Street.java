package com.homepoker.engine.game;

/**
 * 핸드 진행 단계. PREFLOP→FLOP→TURN→RIVER 각각에 베팅 라운드가 있고,
 * SHOWDOWN 에서 승자를 가리며 COMPLETE 는 팟 분배까지 끝난 종료 상태.
 */
public enum Street {
    PREFLOP,
    FLOP,
    TURN,
    RIVER,
    SHOWDOWN,
    COMPLETE
}
