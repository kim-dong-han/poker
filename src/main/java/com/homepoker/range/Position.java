package com.homepoker.range;

/**
 * 6-max 포지션. RFI(Raise First In) 오프닝 레인지는 뒤로 갈수록(BTN) 넓어진다.
 * BB 는 폴드로 돌아왔을 때 이미 베팅이 없으므로 오프닝 개념이 없다(레인지 비어 있음).
 */
public enum Position {
    UTG, HJ, CO, BTN, SB, BB
}
