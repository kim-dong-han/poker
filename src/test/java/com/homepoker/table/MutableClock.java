package com.homepoker.table;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** 테스트에서 시간을 임의로 흘려보내기 위한 조작 가능한 Clock. */
class MutableClock extends Clock {

    private Instant now;
    private final ZoneId zone;

    MutableClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    static MutableClock startingNow() {
        return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
    }

    void advance(Duration by) {
        now = now.plus(by);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId z) {
        return new MutableClock(now, z);
    }
}
