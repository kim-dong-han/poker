package com.homepoker.stats;

import com.homepoker.web.dto.LeaderboardRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsServiceTest {

    private static HandReport report(Map<String, Long> netDelta, Set<String> winners,
                                     Set<String> vpip, Set<String> pfr) {
        Map<String, String> names = Map.of("A", "Alice", "B", "Bob");
        return new HandReport(names, netDelta.keySet(), vpip, pfr, netDelta, winners);
    }

    @Test
    void accumulatesNetAndCountsAndSortsLeaderboard() {
        StatsService stats = new StatsService();
        // 핸드1: A가 +30, B가 -30. A는 프리플랍 레이즈(자발적+레이즈), B는 콜(자발적).
        stats.record(report(Map.of("A", 30L, "B", -30L), Set.of("A"), Set.of("A", "B"), Set.of("A")));
        // 핸드2: B가 +50, A가 -50. B 레이즈, A 폴드(비자발).
        stats.record(report(Map.of("A", -50L, "B", 50L), Set.of("B"), Set.of("B"), Set.of("B")));

        List<LeaderboardRow> board = stats.leaderboard();
        assertEquals(2, board.size());
        // net: A=-20, B=+20 → B가 1위
        assertEquals("B", board.get(0).playerId());
        assertEquals(20L, board.get(0).netProfit());
        assertEquals(-20L, board.get(1).netProfit());

        LeaderboardRow a = board.stream().filter(r -> r.playerId().equals("A")).findFirst().orElseThrow();
        assertEquals(2, a.handsPlayed());
        assertEquals(1, a.handsWon());
        assertEquals(50, a.vpip()); // 2핸드 중 1핸드 자발적 → 50%
        assertEquals(50, a.pfr());  // 2핸드 중 1핸드 레이즈 → 50%
    }

    @Test
    void vpipAndPfrAreZeroWithNoVoluntaryPlay() {
        StatsService stats = new StatsService();
        stats.record(report(Map.of("A", -10L, "B", 10L), Set.of("B"), Set.of(), Set.of()));
        LeaderboardRow a = stats.leaderboard().stream()
                .filter(r -> r.playerId().equals("A")).findFirst().orElseThrow();
        assertEquals(0, a.vpip());
        assertEquals(0, a.pfr());
        assertEquals(1, a.handsPlayed());
    }
}
