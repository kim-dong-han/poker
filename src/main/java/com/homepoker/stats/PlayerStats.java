package com.homepoker.stats;

/**
 * 한 플레이어의 누적 통계. VPIP/PFR 는 포커에서 플레이 성향을 보는 핵심 지표다.
 *  - VPIP(Voluntarily Put money In Pot): 프리플랍에 자발적으로 칩을 넣은 핸드 비율
 *  - PFR(PreFlop Raise): 프리플랍에 레이즈한 핸드 비율
 *  - net: 누적 순손익(칩), ROI 리더보드 정렬 기준
 */
public class PlayerStats {

    private final String playerId;
    private String name;
    private int handsPlayed;
    private int vpipHands;
    private int pfrHands;
    private int handsWon;
    private long netProfit;

    public PlayerStats(String playerId) {
        this.playerId = playerId;
    }

    void setName(String name) {
        this.name = name;
    }

    void addHand(boolean vpip, boolean pfr, boolean won, long netDelta) {
        handsPlayed++;
        if (vpip) {
            vpipHands++;
        }
        if (pfr) {
            pfrHands++;
        }
        if (won) {
            handsWon++;
        }
        netProfit += netDelta;
    }

    public String playerId() {
        return playerId;
    }

    public String name() {
        return name;
    }

    public int handsPlayed() {
        return handsPlayed;
    }

    public int handsWon() {
        return handsWon;
    }

    public long netProfit() {
        return netProfit;
    }

    public double vpip() {
        return handsPlayed == 0 ? 0 : (double) vpipHands / handsPlayed;
    }

    public double pfr() {
        return handsPlayed == 0 ? 0 : (double) pfrHands / handsPlayed;
    }
}
