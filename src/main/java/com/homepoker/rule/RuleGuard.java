package com.homepoker.rule;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 자금 규칙 집행기. 착석/재입장/리로드를 가로채 정책을 강제한다.
 * 시계(Clock)를 주입받아 쿨다운·일일 한도를 결정적으로 테스트할 수 있다.
 *
 * 상태(플레이어별 버스트 시각·오늘 리로드 횟수)만 보유하고 게임 규칙은 모른다 —
 * 순수 엔진과 분리된 '정책' 계층.
 */
@Component
public class RuleGuard {

    private final BuyInPolicy policy;
    private final Clock clock;
    private final ZoneId zone;
    private final Map<String, Ledger> ledgers = new ConcurrentHashMap<>();

    public RuleGuard(BuyInPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
        this.zone = clock.getZone();
    }

    public BuyInPolicy policy() {
        return policy;
    }

    /** 착석(초기 바이인). 스택 범위 + 버스트 재입장 쿨다운을 검사한다. */
    public void checkJoin(String playerId, long buyIn) {
        checkAmountInRange(buyIn);
        Ledger ledger = ledgers.get(playerId);
        if (ledger != null && ledger.bustAt != null) {
            Instant readyAt = ledger.bustAt.plus(policy.reentryCooldown());
            Instant now = clock.instant();
            if (now.isBefore(readyAt)) {
                long secs = Duration.between(now, readyAt).toSeconds();
                throw new RuleViolation("버스트 후 재입장 쿨다운 중입니다. " + secs + "초 후 가능합니다.");
            }
        }
    }

    /**
     * 리로드(착석 상태에서 칩 추가). 하루 횟수 상한과, 리로드 후 스택이 maxBuyIn 을
     * 넘지 않는지 검사한 뒤 카운트를 올린다.
     *
     * @param currentStack 현재 스택
     * @param addAmount    추가하려는 칩
     */
    public void checkAndRecordReload(String playerId, long currentStack, long addAmount) {
        if (addAmount <= 0) {
            throw new RuleViolation("리로드 금액은 0보다 커야 합니다.");
        }
        if (currentStack + addAmount > policy.maxBuyIn()) {
            throw new RuleViolation("리로드 후 스택이 최대 바이인(" + policy.maxBuyIn() + ")을 초과합니다.");
        }
        Ledger ledger = ledgers.computeIfAbsent(playerId, id -> new Ledger());
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        if (!today.equals(ledger.reloadDay)) {
            ledger.reloadDay = today;
            ledger.reloadsToday = 0;
        }
        if (ledger.reloadsToday >= policy.maxReloadsPerDay()) {
            throw new RuleViolation("오늘 리로드 한도(" + policy.maxReloadsPerDay() + "회)를 모두 사용했습니다.");
        }
        ledger.reloadsToday++;
    }

    /** 플레이어가 파산(스택 0)했음을 기록 → 재입장 쿨다운 시작. */
    public void recordBust(String playerId) {
        ledgers.computeIfAbsent(playerId, id -> new Ledger()).bustAt = clock.instant();
    }

    /** 남은 재입장 쿨다운(초). 없으면 0. UI 표시용. */
    public long reentryCooldownRemainingSeconds(String playerId) {
        Ledger ledger = ledgers.get(playerId);
        if (ledger == null || ledger.bustAt == null) {
            return 0;
        }
        Instant readyAt = ledger.bustAt.plus(policy.reentryCooldown());
        long secs = Duration.between(clock.instant(), readyAt).toSeconds();
        return Math.max(0, secs);
    }

    private void checkAmountInRange(long amount) {
        if (amount < policy.minBuyIn()) {
            throw new RuleViolation("최소 바이인은 " + policy.minBuyIn() + " 입니다.");
        }
        if (amount > policy.maxBuyIn()) {
            throw new RuleViolation("최대 바이인은 " + policy.maxBuyIn() + " 입니다.");
        }
    }

    private static final class Ledger {
        private Instant bustAt;         // 마지막 버스트 시각
        private LocalDate reloadDay;    // reloadsToday 가 집계된 날짜
        private int reloadsToday;
    }
}
