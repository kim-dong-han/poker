package com.homepoker.table;

import com.homepoker.equity.EquityService;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import com.homepoker.web.dto.TableStateView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeBankTest {

    private final MutableClock clock = MutableClock.startingNow();
    private final TurnTimer timer = new TurnTimer(clock, Duration.ofSeconds(30));
    private final TableService service = new TableService(
            new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
            new EquityService(),
            new StatsService(),
            timer);

    private void seatTwoAndStart() {
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        service.startHand("t1");
    }

    @Test
    void noTimeoutBeforeDeadline() {
        seatTwoAndStart();
        clock.advance(Duration.ofSeconds(29));
        assertFalse(service.enforceTimeout("t1"), "마감 전에는 자동 액션 없음");
    }

    @Test
    void timeoutAutoActsForCurrentActor() {
        seatTwoAndStart();
        String actorBefore = service.viewFor("t1", "alice").currentActorId();

        clock.advance(Duration.ofSeconds(31));
        assertTrue(service.enforceTimeout("t1"), "마감 후 자동 액션이 들어가야 함");

        // 자동 액션으로 차례가 넘어갔다(또는 핸드가 진행됐다)
        String actorAfter = service.viewFor("t1", "alice").currentActorId();
        assertNotEquals(actorBefore, actorAfter);
    }

    @Test
    void timeoutFoldsWhenFacingBetOtherwiseChecks() {
        seatTwoAndStart();
        // 프리플랍 SB(=현재 액션자)는 맞출 벳이 있으므로 자동 폴드 → 상대 무혈입성으로 핸드 종료
        clock.advance(Duration.ofSeconds(31));
        service.enforceTimeout("t1");
        TableStateView end = service.viewFor("t1", "alice");
        assertFalse(end.handInProgress(), "SB 자동 폴드로 핸드가 끝나야 함");
        assertFalse(end.payouts().isEmpty());
    }

    @Test
    void viewExposesSecondsLeftAndClearsOnHandEnd() {
        seatTwoAndStart();
        long left = service.viewFor("t1", "alice").turnSecondsLeft();
        assertTrue(left > 0 && left <= 30);

        clock.advance(Duration.ofSeconds(31));
        service.enforceTimeout("t1"); // SB 폴드 → 핸드 종료 → 타이머 해제
        assertEquals(0, service.viewFor("t1", "alice").turnSecondsLeft());
    }

    @Test
    void enforceTimeoutIsIdempotentAndSafeWithNoActiveHand() {
        // 핸드가 없을 때 호출해도 조용히 false
        assertFalse(service.enforceTimeout("empty"));
    }
}
