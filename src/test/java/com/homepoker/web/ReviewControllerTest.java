package com.homepoker.web;

import com.homepoker.equity.EquityService;
import com.homepoker.review.HandReview;
import com.homepoker.review.HandReviewer;
import com.homepoker.review.SessionReviewRow;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import com.homepoker.table.TableService;
import com.homepoker.table.TurnTimer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewControllerTest {

    private static TableService newTableService() {
        return new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(),
                new StatsService(),
                new TurnTimer(Clock.systemDefaultZone()));
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
    void reviewEndpointReturnsJudgedDecisions() {
        TableService tableService = newTableService();
        ReviewController controller = new ReviewController(
                tableService, new HandReviewer(new EquityService()));
        tableService.join("t1", "alice", "Alice", 1000);
        tableService.join("t1", "bob", "Bob", 1000);
        tableService.startHand("t1");
        playOutOneHand(tableService, "t1");

        HandReview review = controller.review("t1", 0);
        assertNotNull(review.assumption());
        // 체크/콜 진행이라 최소한 프리플랍 콜 1개는 판정 대상
        assertFalse(review.decisions().isEmpty());
        assertTrue(review.decisions().stream().allMatch(d ->
                d.action().equals("CALL") || d.action().equals("FOLD")));

        assertThrows(IllegalArgumentException.class, () -> controller.review("t1", 99));
    }

    // history 는 최신이 index 0 — 새 핸드가 끝나 인덱스가 밀려도 같은 핸드는 캐시가 재사용돼야 한다.
    @Test
    void cacheSurvivesIndexShiftWhenNewHandCompletes() {
        TableService tableService = newTableService();
        ReviewController controller = new ReviewController(
                tableService, new HandReviewer(new EquityService()));
        tableService.join("t1", "alice", "Alice", 1000);
        tableService.join("t1", "bob", "Bob", 1000);
        tableService.startHand("t1");
        playOutOneHand(tableService, "t1");

        HandReview first = controller.review("t1", 0);

        tableService.startHand("t1");
        playOutOneHand(tableService, "t1");

        // 첫 핸드는 이제 index 1 — 몬테카를로 재계산 없이 동일 인스턴스가 나와야 한다
        assertSame(first, controller.review("t1", 1));
    }

    @Test
    void sessionReportAggregatesPerPlayer() {
        TableService tableService = newTableService();
        ReviewController controller = new ReviewController(
                tableService, new HandReviewer(new EquityService()));
        tableService.join("t1", "alice", "Alice", 1000);
        tableService.join("t1", "bob", "Bob", 1000);
        tableService.startHand("t1");
        playOutOneHand(tableService, "t1");
        tableService.startHand("t1");
        playOutOneHand(tableService, "t1");

        List<SessionReviewRow> rows = controller.session("t1");
        assertFalse(rows.isEmpty());
        for (SessionReviewRow r : rows) {
            assertTrue(r.decisions() >= r.mistakes());
            assertTrue(r.totalEvLossBb() >= 0.0);
        }
        // 빈 테이블은 빈 리포트
        assertEquals(List.of(), controller.session("빈테이블"));
    }
}
