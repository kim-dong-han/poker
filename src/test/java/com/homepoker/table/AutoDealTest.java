package com.homepoker.table;

import com.homepoker.equity.EquityService;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoDealTest {

    private final MutableClock clock = MutableClock.startingNow();
    private final TableService service = new TableService(
            new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
            new EquityService(),
            new StatsService(),
            new TurnTimer(clock));
    private final AutoDealService auto = new AutoDealService(service, clock, 4000);

    /** 두 명 착석 후 SB 폴드로 핸드를 즉시 끝낸다. */
    private void finishOneHand() {
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        service.startHand("t1");
        String actor = service.viewFor("t1", "alice").currentActorId();
        service.applyAction("t1", actor, "FOLD", 0);
    }

    @Test
    void dealsNextHandAfterDelay() {
        finishOneHand();
        assertFalse(auto.dealIfDue("t1"), "종료를 처음 본 시점엔 예약만 한다");
        clock.advance(Duration.ofMillis(3900));
        assertFalse(auto.dealIfDue("t1"), "대기 시간 전에는 시작하지 않는다");
        clock.advance(Duration.ofMillis(200));
        assertTrue(auto.dealIfDue("t1"), "대기 시간이 지나면 자동 시작");
        assertTrue(service.viewFor("t1", "alice").handInProgress());
    }

    /** 체크로만 리버까지 가서 쇼다운으로 끝낸다(올인 런아웃 연출 대기 검증용). */
    private void finishShowdownHand() {
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        service.startHand("t1");
        service.applyAction("t1", service.viewFor("t1", "alice").currentActorId(), "CALL", 0);
        service.applyAction("t1", service.viewFor("t1", "alice").currentActorId(), "CHECK", 0);
        for (int street = 0; street < 3; street++) { // 플랍·턴·리버 체크-체크
            service.applyAction("t1", service.viewFor("t1", "alice").currentActorId(), "CHECK", 0);
            service.applyAction("t1", service.viewFor("t1", "alice").currentActorId(), "CHECK", 0);
        }
    }

    // 쇼다운으로 끝난 핸드는 카드 공개·결과 감상 시간(+3.5초)을 더 기다린 뒤 자동 시작한다.
    @Test
    void showdownHandWaitsExtraBeforeAutoDeal() {
        finishShowdownHand();
        assertFalse(auto.dealIfDue("t1"), "종료를 처음 본 시점엔 예약만 한다");
        clock.advance(Duration.ofMillis(7400)); // 기본 4000 + 쇼다운 3500 직전
        assertFalse(auto.dealIfDue("t1"), "쇼다운은 추가 대기가 끝나기 전 시작하지 않는다");
        clock.advance(Duration.ofMillis(200));
        assertTrue(auto.dealIfDue("t1"), "추가 대기가 지나면 자동 시작");
    }

    @Test
    void doesNothingWhileHandInProgress() {
        finishOneHand();
        auto.dealIfDue("t1");
        clock.advance(Duration.ofSeconds(5));
        auto.dealIfDue("t1"); // 자동 시작됨
        clock.advance(Duration.ofSeconds(10));
        assertFalse(auto.dealIfDue("t1"), "진행 중에는 아무것도 하지 않는다");
    }

    @Test
    void disabledTableNeverAutoDeals() {
        auto.setEnabled("t1", false);
        finishOneHand();
        assertFalse(auto.dealIfDue("t1"));
        clock.advance(Duration.ofSeconds(10));
        assertFalse(auto.dealIfDue("t1"), "꺼진 테이블은 자동 시작하지 않는다");
        assertFalse(service.viewFor("t1", "alice").handInProgress());
    }

    @Test
    void noAutoDealBeforeFirstManualHand() {
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        clock.advance(Duration.ofSeconds(10));
        assertFalse(auto.dealIfDue("t1"), "첫 핸드는 사람이 시작해야 한다");
    }

    @Test
    void stopsWhenFewerThanTwoPlayersWithChips() {
        finishOneHand();
        service.getOrCreate("t1").removeSeat("bob"); // 버스트로 자리가 비워진 상황을 흉내
        auto.dealIfDue("t1"); // 예약
        clock.advance(Duration.ofSeconds(5));
        assertFalse(auto.dealIfDue("t1"), "칩 보유 2명 미만이면 멈춘다");
        assertFalse(service.viewFor("t1", "alice").handInProgress());
    }

    @Test
    void togglingOffCancelsPendingDeal() {
        finishOneHand();
        auto.dealIfDue("t1"); // 예약
        auto.setEnabled("t1", false);
        clock.advance(Duration.ofSeconds(5));
        assertFalse(auto.dealIfDue("t1"), "끄면 예약된 자동 시작도 취소된다");
    }
}
