package com.homepoker.engine.eval;

/**
 * 포커 핸드 족보. ordinal 이 클수록 강하다(HIGH_CARD 최약 ... STRAIGHT_FLUSH 최강).
 * 로열 플러시는 별도 족보가 아니라 A-high 스트레이트 플러시로 취급한다.
 */
public enum HandCategory {
    HIGH_CARD,
    ONE_PAIR,
    TWO_PAIR,
    THREE_OF_A_KIND,
    STRAIGHT,
    FLUSH,
    FULL_HOUSE,
    FOUR_OF_A_KIND,
    STRAIGHT_FLUSH
}
