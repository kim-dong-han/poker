package com.homepoker.rule;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleGuardTest {

    /** 테스트에서 시간을 임의로 흐르게 하는 시계. */
    static final class MutableClock extends Clock {
        private Instant now;
        private final ZoneId zone;

        MutableClock(Instant start, ZoneId zone) {
            this.now = start;
            this.zone = zone;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() {
            return zone;
        }

        @Override public Clock withZone(ZoneId z) {
            return new MutableClock(now, z);
        }

        @Override public Instant instant() {
            return now;
        }
    }

    private static final BuyInPolicy POLICY =
            new BuyInPolicy(100, 1000, Duration.ofMinutes(10), 2);

    private MutableClock clock() {
        return new MutableClock(Instant.parse("2026-07-06T12:00:00Z"), ZoneId.of("UTC"));
    }

    // ---- 최소/최대 바이인 ----
    @Test
    void rejectsBuyInBelowMinOrAboveMax() {
        RuleGuard guard = new RuleGuard(POLICY, clock());
        assertThrows(RuleViolation.class, () -> guard.checkJoin("p", 99));
        assertThrows(RuleViolation.class, () -> guard.checkJoin("p", 1001));
        assertDoesNotThrow(() -> guard.checkJoin("p", 100));
        assertDoesNotThrow(() -> guard.checkJoin("p", 1000));
    }

    // ---- 버스트 후 재입장 쿨다운 ----
    @Test
    void blocksReentryDuringCooldownThenAllows() {
        MutableClock clock = clock();
        RuleGuard guard = new RuleGuard(POLICY, clock);

        assertDoesNotThrow(() -> guard.checkJoin("p", 500)); // 최초 착석 OK
        guard.recordBust("p");                                // 파산

        // 5분 경과 — 아직 쿨다운(10분) 중
        clock.advance(Duration.ofMinutes(5));
        assertThrows(RuleViolation.class, () -> guard.checkJoin("p", 500));
        assertTrue(guard.reentryCooldownRemainingSeconds("p") > 0);

        // 추가 6분 경과 — 총 11분, 쿨다운 종료
        clock.advance(Duration.ofMinutes(6));
        assertDoesNotThrow(() -> guard.checkJoin("p", 500));
        assertEquals(0, guard.reentryCooldownRemainingSeconds("p"));
    }

    @Test
    void cooldownDoesNotAffectOtherPlayers() {
        MutableClock clock = clock();
        RuleGuard guard = new RuleGuard(POLICY, clock);
        guard.recordBust("busted");
        clock.advance(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> guard.checkJoin("fresh", 500));
    }

    // ---- 일일 리로드 한도 ----
    @Test
    void enforcesDailyReloadLimitAndResetsNextDay() {
        MutableClock clock = clock();
        RuleGuard guard = new RuleGuard(POLICY, clock);

        guard.checkAndRecordReload("p", 200, 100); // 1회
        guard.checkAndRecordReload("p", 300, 100); // 2회 (한도=2)
        assertThrows(RuleViolation.class,
                () -> guard.checkAndRecordReload("p", 400, 100)); // 3회 거부

        // 다음 날로 넘어가면 카운트 리셋
        clock.advance(Duration.ofDays(1));
        assertDoesNotThrow(() -> guard.checkAndRecordReload("p", 200, 100));
    }

    // ---- 리바인(버스트 직후 즉시 재착석) ----
    @Test
    void rebuyWaivesCooldownImmediatelyAfterBust() {
        MutableClock clock = clock();
        RuleGuard guard = new RuleGuard(POLICY, clock);
        guard.recordBust("p");

        // 일반 재입장은 쿨다운에 막히지만 리바인은 즉시 가능
        assertThrows(RuleViolation.class, () -> guard.checkJoin("p", 500));
        assertDoesNotThrow(() -> guard.checkAndRecordRebuy("p", 500));
        assertEquals(1, guard.rebuysUsed("p"));
        // 리바인으로 복귀했으므로 남은 쿨다운도 해소
        assertEquals(0, guard.reentryCooldownRemainingSeconds("p"));
    }

    @Test
    void rebuyUnlimitedWhenLimitNotApplied() {
        RuleGuard guard = new RuleGuard(POLICY, clock()); // maxRebuys=0(미적용)
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> guard.checkAndRecordRebuy("p", 500));
        }
        assertEquals(10, guard.rebuysUsed("p"));
    }

    @Test
    void rebuyLimitEnforcedWhenConfigured() {
        BuyInPolicy limited = new BuyInPolicy(100, 1000, Duration.ofMinutes(10), 2, 2);
        RuleGuard guard = new RuleGuard(limited, clock());
        guard.checkAndRecordRebuy("p", 500); // 1회
        guard.checkAndRecordRebuy("p", 500); // 2회 (한도=2)
        assertThrows(RuleViolation.class, () -> guard.checkAndRecordRebuy("p", 500));
    }

    @Test
    void rebuyStillEnforcesBuyInRange() {
        RuleGuard guard = new RuleGuard(POLICY, clock());
        assertThrows(RuleViolation.class, () -> guard.checkAndRecordRebuy("p", 99));
        assertThrows(RuleViolation.class, () -> guard.checkAndRecordRebuy("p", 1001));
    }

    @Test
    void rejectsReloadThatExceedsMaxBuyIn() {
        RuleGuard guard = new RuleGuard(POLICY, clock());
        // 현재 950 + 100 = 1050 > max(1000)
        assertThrows(RuleViolation.class, () -> guard.checkAndRecordReload("p", 950, 100));
    }

    @Test
    void rejectsNonPositiveReload() {
        RuleGuard guard = new RuleGuard(POLICY, clock());
        assertThrows(RuleViolation.class, () -> guard.checkAndRecordReload("p", 200, 0));
    }
}
