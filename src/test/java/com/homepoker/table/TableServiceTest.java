package com.homepoker.table;

import com.homepoker.equity.EquityService;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import com.homepoker.web.dto.SeatView;
import com.homepoker.web.dto.TableStateView;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableServiceTest {

    private static TableService newService() {
        return new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(),
                new StatsService());
    }

    private static SeatView seat(TableStateView view, String playerId) {
        return view.seats().stream()
                .filter(s -> s.playerId().equals(playerId))
                .findFirst().orElseThrow();
    }

    private TableService twoPlayerTableMidHand() {
        TableService service = newService();
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        service.startHand("t1");
        return service;
    }

    @Test
    void lobbyViewBeforeHand() {
        TableService service = newService();
        service.join("t1", "alice", "Alice", 1000);
        TableStateView view = service.viewFor("t1", "alice");
        assertFalse(view.handInProgress());
        assertEquals("WAITING", view.street());
        assertEquals(1, view.seats().size());
    }

    @Test
    void viewerSeesOwnCardsButNotOpponents() {
        TableService service = twoPlayerTableMidHand();
        TableStateView aliceView = service.viewFor("t1", "alice");

        assertTrue(aliceView.handInProgress());
        assertEquals("PREFLOP", aliceView.street());
        assertEquals(2, seat(aliceView, "alice").holeCards().size()); // 본인 카드 보임
        assertNull(seat(aliceView, "bob").holeCards());               // 상대 카드 숨김
    }

    @Test
    void currentActorHasLegalActions() {
        TableService service = twoPlayerTableMidHand();
        TableStateView anyView = service.viewFor("t1", "alice");
        String actor = anyView.currentActorId();
        assertNotNull(actor);

        TableStateView actorView = service.viewFor("t1", actor);
        assertFalse(actorView.viewerLegalActions().isEmpty());
    }

    @Test
    void showdownRevealsOpponentCardsAndConservesChips() {
        TableService service = twoPlayerTableMidHand();

        // 체크/콜로 리버까지 진행 → 반드시 쇼다운
        int guard = 0;
        while (service.viewFor("t1", "alice").handInProgress()) {
            if (guard++ > 100) {
                throw new AssertionError("핸드가 끝나지 않음");
            }
            TableStateView v = service.viewFor("t1", "alice");
            String actor = v.currentActorId();
            var legal = service.viewFor("t1", actor).viewerLegalActions();
            if (legal.contains("CHECK")) {
                service.applyAction("t1", actor, "CHECK", 0);
            } else if (legal.contains("CALL")) {
                service.applyAction("t1", actor, "CALL", 0);
            } else {
                service.applyAction("t1", actor, "FOLD", 0);
            }
        }

        TableStateView end = service.viewFor("t1", "alice");
        assertFalse(end.handInProgress());
        assertFalse(end.payouts().isEmpty());
        // 쇼다운이므로 상대 카드도 공개됨
        assertNotNull(seat(end, "bob").holeCards());

        long totalStacks = end.seats().stream().mapToLong(SeatView::stack).sum();
        assertEquals(2000L, totalStacks);
    }

    @Test
    void recordsZeroSumStatsAfterHand() {
        StatsService stats = new StatsService();
        TableService service = new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(), stats);
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        service.startHand("t1");

        int guard = 0;
        while (service.viewFor("t1", "alice").handInProgress()) {
            if (guard++ > 100) {
                throw new AssertionError("핸드가 끝나지 않음");
            }
            String actor = service.viewFor("t1", "alice").currentActorId();
            var legal = service.viewFor("t1", actor).viewerLegalActions();
            service.applyAction("t1", actor, legal.contains("CHECK") ? "CHECK" : "CALL", 0);
        }

        var board = stats.leaderboard();
        assertEquals(2, board.size());
        assertEquals(2, board.stream().mapToInt(r -> r.handsPlayed()).sum()); // 각자 1핸드
        assertEquals(0L, board.stream().mapToLong(r -> r.netProfit()).sum()); // 제로섬
    }

    @Test
    void actingOutOfTurnIsRejected() {
        TableService service = twoPlayerTableMidHand();
        TableStateView v = service.viewFor("t1", "alice");
        String actor = v.currentActorId();
        String other = actor.equals("alice") ? "bob" : "alice";
        // 차례가 아닌 사람이 액션하면 엔진이 거부
        try {
            service.applyAction("t1", other, "CHECK", 0);
            throw new AssertionError("차례 아닌 액션이 통과됨");
        } catch (IllegalStateException expected) {
            // ok
        }
    }
}
