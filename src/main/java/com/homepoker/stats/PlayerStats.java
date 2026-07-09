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
    // 해링턴 상대 모델링 수치의 원료(AF/WtSD/F3B)
    private int flopsSeen;
    private int showdowns;
    private int postflopAggr;
    private int postflopCalls;
    private int facedThreeBet;
    private int foldedToThreeBet;

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

    void addPostflop(boolean sawFlop, int aggr, int calls, boolean showdown,
                     boolean faced3Bet, boolean folded3Bet) {
        if (sawFlop) {
            flopsSeen++;
        }
        if (showdown) {
            showdowns++;
        }
        postflopAggr += aggr;
        postflopCalls += calls;
        if (faced3Bet) {
            facedThreeBet++;
        }
        if (folded3Bet) {
            foldedToThreeBet++;
        }
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

    /** AF = 포스트플랍 (벳+레이즈) ÷ 콜. 콜 0회면 공격 횟수 자체를 반환(무한대 근사). */
    public double af() {
        return postflopCalls == 0 ? postflopAggr : (double) postflopAggr / postflopCalls;
    }

    /** WtSD = 플랍 본 핸드 중 쇼다운 도달 비율. */
    public double wtsd() {
        return flopsSeen == 0 ? 0 : (double) showdowns / flopsSeen;
    }

    /** F3B = 오픈 후 3벳 마주친 핸드 중 폴드 비율. */
    public double f3b() {
        return facedThreeBet == 0 ? 0 : (double) foldedToThreeBet / facedThreeBet;
    }

    /** 포스트플랍 표본 수(AF 신뢰도 판단용). */
    public int postflopSamples() {
        return postflopAggr + postflopCalls;
    }

    // --- 영속화(스냅샷) ---

    PlayerStatsSnapshot toSnapshot() {
        return new PlayerStatsSnapshot(playerId, name, handsPlayed, vpipHands, pfrHands, handsWon, netProfit,
                flopsSeen, showdowns, postflopAggr, postflopCalls, facedThreeBet, foldedToThreeBet);
    }

    static PlayerStats fromSnapshot(PlayerStatsSnapshot s) {
        PlayerStats ps = new PlayerStats(s.playerId());
        ps.name = s.name();
        ps.handsPlayed = s.handsPlayed();
        ps.vpipHands = s.vpipHands();
        ps.pfrHands = s.pfrHands();
        ps.handsWon = s.handsWon();
        ps.netProfit = s.netProfit();
        ps.flopsSeen = s.flopsSeen();
        ps.showdowns = s.showdowns();
        ps.postflopAggr = s.postflopAggr();
        ps.postflopCalls = s.postflopCalls();
        ps.facedThreeBet = s.facedThreeBet();
        ps.foldedToThreeBet = s.foldedToThreeBet();
        return ps;
    }
}
