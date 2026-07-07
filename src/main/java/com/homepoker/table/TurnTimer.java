package com.homepoker.table;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테이블별 "현재 액션자의 제한시간" 추적기. 타이밍은 순수 게임 규칙이 아니라 운영 관심사이므로
 * 엔진(HandEngine) 밖의 이 계층에 둔다 — 엔진은 여전히 시간을 전혀 모른다.
 *
 * Clock 을 주입받아 운영에선 시스템 시계, 테스트에선 조작 가능한 시계를 쓴다(RuleGuard 와 동일 패턴).
 */
@Component
public class TurnTimer {

    /** 액션 제한시간(포트폴리오 기본값). */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final Clock clock;
    private final Duration timeout;
    private final Map<String, Instant> deadlines = new ConcurrentHashMap<>();

    @Autowired
    public TurnTimer(Clock clock) {
        this(clock, DEFAULT_TIMEOUT);
    }

    /** 테스트에서 짧은 타임아웃을 주입하기 위한 생성자. */
    public TurnTimer(Clock clock, Duration timeout) {
        this.clock = clock;
        this.timeout = timeout;
    }

    /** 현재 액션자의 제한시간을 지금부터 timeout 뒤로 새로 건다. */
    public void startTurn(String tableId) {
        deadlines.put(tableId, clock.instant().plus(timeout));
    }

    public void clear(String tableId) {
        deadlines.remove(tableId);
    }

    /** 마감이 걸려 있고 이미 지났는가. 걸려 있지 않으면 false. */
    public boolean isExpired(String tableId) {
        Instant deadline = deadlines.get(tableId);
        return deadline != null && !clock.instant().isBefore(deadline);
    }

    /** UI 카운트다운용 남은 초(마감 없으면 0). */
    public long secondsLeft(String tableId) {
        Instant deadline = deadlines.get(tableId);
        if (deadline == null) {
            return 0;
        }
        long ms = deadline.toEpochMilli() - clock.millis();
        return Math.max(0, ms / 1000);
    }

    public Duration timeout() {
        return timeout;
    }
}
