package com.homepoker.table;

import com.homepoker.equity.EquityService;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import com.homepoker.web.dto.HandSummaryView;
import com.homepoker.web.dto.ReplayFrame;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayServiceTest {

    private static TableService newTableService() {
        return new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(),
                new StatsService());
    }

    /** 한 핸드를 체크/콜로 끝까지 진행시킨다. */
    private static void playOutOneHand(TableService service, String tableId) {
        int guard = 0;
        while (service.viewFor(tableId, "alice").handInProgress()) {
            if (guard++ > 100) {
                throw new AssertionError("핸드가 끝나지 않음");
            }
            String actor = service.viewFor(tableId, "alice").currentActorId();
            var legal = service.viewFor(tableId, actor).viewerLegalActions();
            service.applyAction(tableId, actor, legal.contains("CHECK") ? "CHECK" : "CALL", 0);
        }
    }

    @Test
    void completedHandIsArchivedWithFrames() {
        TableService tableService = newTableService();
        ReplayService replay = new ReplayService(tableService);
        tableService.join("t1", "alice", "Alice", 1000);
        tableService.join("t1", "bob", "Bob", 1000);
        tableService.startHand("t1");
        playOutOneHand(tableService, "t1");

        List<HandSummaryView> summaries = replay.summaries("t1");
        assertEquals(1, summaries.size());
        HandSummaryView s = summaries.get(0);
        assertEquals(0, s.index());
        assertTrue(s.actionCount() >= 1);

        List<ReplayFrame> frames = replay.frames("t1", 0);
        // 프레임 수 = 액션 수 + 1(초기 프레임)
        assertEquals(s.actionCount() + 1, frames.size());
        assertNull(frames.get(0).action());                 // 첫 프레임은 액션 없음
        assertEquals("PREFLOP", frames.get(0).street());
        // 마지막 프레임은 종료 상태이고 분배가 채워져 있다
        ReplayFrame last = frames.get(frames.size() - 1);
        assertTrue(last.payouts().values().stream().anyMatch(v -> v > 0));
    }

    @Test
    void replayFrameRevealsAllHoleCards() {
        TableService tableService = newTableService();
        ReplayService replay = new ReplayService(tableService);
        tableService.join("t1", "alice", "Alice", 1000);
        tableService.join("t1", "bob", "Bob", 1000);
        tableService.startHand("t1");
        playOutOneHand(tableService, "t1");

        ReplayFrame first = replay.frames("t1", 0).get(0);
        // 지난 핸드 리뷰이므로 모든 좌석의 홀카드가 공개된다(리댁션 없음)
        assertTrue(first.seats().stream().allMatch(seat -> seat.holeCards().size() == 2));
    }

    @Test
    void multipleHandsAreArchivedNewestFirst() {
        TableService tableService = newTableService();
        ReplayService replay = new ReplayService(tableService);
        tableService.join("t1", "alice", "Alice", 1000);
        tableService.join("t1", "bob", "Bob", 1000);
        tableService.startHand("t1");
        playOutOneHand(tableService, "t1");
        tableService.startHand("t1");
        playOutOneHand(tableService, "t1");

        List<HandSummaryView> summaries = replay.summaries("t1");
        assertEquals(2, summaries.size());
        assertEquals(0, summaries.get(0).index()); // 최신이 앞
        assertEquals(1, summaries.get(1).index());
    }
}
