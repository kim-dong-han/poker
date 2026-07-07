package com.homepoker.stats;

import com.homepoker.web.dto.LeaderboardRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsPersistenceTest {

    @TempDir
    Path tmp;

    private static HandReport oneHand(String id, String name, boolean vpip, boolean pfr,
                                      boolean won, long net) {
        return new HandReport(
                Map.of(id, name),
                Set.of(id),
                vpip ? Set.of(id) : Set.of(),
                pfr ? Set.of(id) : Set.of(),
                Map.of(id, net),
                won ? Set.of(id) : Set.of());
    }

    @Test
    void statsSurviveRestartViaFile() {
        Path file = tmp.resolve("stats.json");
        JsonFileStatsStore store = new JsonFileStatsStore(file.toString());

        StatsService first = new StatsService(store);
        first.record(oneHand("alice", "Alice", true, true, true, 250));
        first.record(oneHand("alice", "Alice", true, false, false, -50));
        assertTrue(Files.exists(file), "저장 파일이 생겨야 함");

        // 재시작 시뮬레이션: 같은 파일로 새 서비스 기동
        StatsService restarted = new StatsService(new JsonFileStatsStore(file.toString()));
        List<LeaderboardRow> board = restarted.leaderboard();
        assertEquals(1, board.size());
        LeaderboardRow row = board.get(0);
        assertEquals("alice", row.playerId());
        assertEquals(2, row.handsPlayed());
        assertEquals(1, row.handsWon());
        assertEquals(200L, row.netProfit());     // 250 - 50
        assertEquals(100, row.vpip());            // 2/2 → 100%
        assertEquals(50, row.pfr());              // 1/2 → 50%
    }

    @Test
    void missingFileStartsEmpty() {
        StatsService service = new StatsService(new JsonFileStatsStore(tmp.resolve("none.json").toString()));
        assertTrue(service.leaderboard().isEmpty());
    }

    @Test
    void corruptFileDoesNotBreakStartup() throws Exception {
        Path file = tmp.resolve("stats.json");
        Files.writeString(file, "{ this is not valid json");
        StatsService service = new StatsService(new JsonFileStatsStore(file.toString()));
        assertTrue(service.leaderboard().isEmpty(), "손상 파일은 빈 통계로 시작");
    }

    @Test
    void noArgServiceDoesNotPersist() {
        // NOOP 저장소를 쓰는 인메모리 서비스는 파일을 만들지 않는다
        StatsService inMemory = new StatsService();
        inMemory.record(oneHand("bob", "Bob", true, false, true, 100));
        assertEquals(1, inMemory.leaderboard().size());
    }
}
