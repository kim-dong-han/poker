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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableServiceTest {

    private static TableService newService() {
        return new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(),
                new StatsService(),
                new TurnTimer(Clock.systemDefaultZone()));
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
                new EquityService(), stats,
                new TurnTimer(Clock.systemDefaultZone()));
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

    // 동결 회귀(스크린샷 55·56 실제 원인): 올인에서 져 버스트한 플레이어는 좌석이 즉시
    // 비워지지만, 브로드캐스트 대상에는 남아 마지막 종료 화면을 반드시 받아야 한다.
    @Test
    void bustedPlayerStillReceivesFinalBroadcast() {
        TableService service = newService();
        String busted = null;
        // 동점(스플릿)이면 버스트가 안 나므로 몇 판 반복 — 스택이 같아 한 판이면 대부분 결판
        for (int attempt = 0; attempt < 30 && busted == null; attempt++) {
            service.join("t1", "alice", "Alice", 1000);
            service.join("t1", "bob", "Bob", 1000);
            service.startHand("t1");
            int guard = 0;
            while (service.viewFor("t1", "alice").handInProgress()) {
                if (guard++ > 20) {
                    throw new AssertionError("올인 핸드가 끝나지 않음");
                }
                TableStateView v = service.viewFor("t1", "alice");
                String actor = v.currentActorId();
                TableStateView av = service.viewFor("t1", actor);
                if (av.viewerLegalActions().contains("RAISE")) {
                    SeatView me = seat(av, actor);
                    service.applyAction("t1", actor, "RAISE", me.committedThisStreet() + me.stack());
                } else {
                    service.applyAction("t1", actor, "CALL", 0);
                }
            }
            TableStateView end = service.viewFor("t1", "alice");
            busted = end.seats().stream()
                    .filter(s -> s.stack() == 0)
                    .map(SeatView::playerId)
                    .findFirst().orElse(null);
        }
        assertNotNull(busted, "올인 승부에서 버스트가 나와야 한다(30판 내)");

        assertFalse(service.seatedPlayerIds("t1").contains(busted), "버스트 좌석은 비워진다");
        assertTrue(service.broadcastTargetIds("t1").contains(busted),
                "버스트 플레이어도 종료 브로드캐스트는 받아야 한다(동결 방지)");
        // 그 뷰에는 핸드 종료·팟 분배가 담겨 클라이언트가 결과 화면으로 전환할 수 있다
        TableStateView finalView = service.viewFor("t1", busted);
        assertFalse(finalView.handInProgress());
        assertFalse(finalView.payouts().isEmpty());
    }

    // 리바인: 버스트 직후 쿨다운 없이 즉시 재착석, 착석 중엔 거부.
    @Test
    void rebuyReseatsBustedPlayerImmediately() {
        TableService service = newService();
        service.join("t1", "alice", "Alice", 1000);
        assertThrows(com.homepoker.rule.RuleViolation.class,
                () -> service.rebuy("t1", "alice", "Alice", 1000)); // 착석 중 리바인 금지

        // 버스트 상황을 직접 구성: 좌석 제거 + 버스트 기록과 동등한 상태
        service.getOrCreate("t1").removeSeat("alice");
        service.rebuy("t1", "alice", "Alice", 800);
        assertTrue(service.seatedPlayerIds("t1").contains("alice"));
        TableStateView v = service.viewFor("t1", "alice");
        assertEquals(800, seat(v, "alice").stack());
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
