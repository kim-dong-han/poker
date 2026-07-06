package com.homepoker.equity;

/**
 * 몬테카를로 이퀴티 추정 결과.
 * @param win       단독 승리 확률
 * @param tie       무승부(찹) 확률
 * @param equity    기대 팟 지분(단독승=1, 무승부는 1/동점자수) — 흔히 말하는 "이퀴티 %"
 * @param iterations 사용한 시뮬레이션 횟수
 */
public record Equity(double win, double tie, double equity, int iterations) {

    public int equityPercent() {
        return (int) Math.round(equity * 100);
    }
}
