package com.homepoker.engine.game;

import java.util.List;

/**
 * 하나의 팟(메인 또는 사이드). amount 는 이 팟의 칩, eligiblePlayerIds 는
 * 이 팟을 두고 다툴 자격이 있는(폴드하지 않은) 플레이어들이다.
 */
public record Pot(long amount, List<String> eligiblePlayerIds) {
    public Pot {
        eligiblePlayerIds = List.copyOf(eligiblePlayerIds);
    }
}
