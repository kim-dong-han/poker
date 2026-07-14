package com.homepoker.rule;

import java.time.Duration;

/**
 * 홈게임 자금 규칙 파라미터. 한국 웹보드식 '박아박아'(짧은 스택 올인 반복·무한 리로드)를
 * 코드로 억제하기 위한 축:
 *  - [minBuyIn, maxBuyIn] : 착석/리로드/리바인 시 스택 범위 강제
 *  - reentryCooldown      : 버스트(파산) 후 일반 재입장(join)까지 대기
 *  - maxReloadsPerDay     : 하루 리로드(추가 바이인) 횟수 상한
 *  - maxRebuys            : 버스트 후 리바인 횟수 상한(0 = 무제한, 현재 미적용 기본값)
 */
public record BuyInPolicy(
        long minBuyIn,
        long maxBuyIn,
        Duration reentryCooldown,
        int maxReloadsPerDay,
        int maxRebuys
) {
    public BuyInPolicy {
        if (minBuyIn <= 0 || maxBuyIn < minBuyIn) {
            throw new IllegalArgumentException("바이인 범위 오류");
        }
        if (reentryCooldown.isNegative()) {
            throw new IllegalArgumentException("쿨다운은 음수일 수 없다");
        }
        if (maxReloadsPerDay < 0) {
            throw new IllegalArgumentException("리로드 한도는 음수일 수 없다");
        }
        if (maxRebuys < 0) {
            throw new IllegalArgumentException("리바인 한도는 음수일 수 없다");
        }
    }

    /** 기존 4-인자 호환 생성자: 리바인 무제한(한도 미적용). */
    public BuyInPolicy(long minBuyIn, long maxBuyIn, Duration reentryCooldown, int maxReloadsPerDay) {
        this(minBuyIn, maxBuyIn, reentryCooldown, maxReloadsPerDay, 0);
    }

    /** 20/100 빅블라인드 기준 기본값(sb=10,bb=20 테이블: 400~2000), 재입장 10분, 하루 3회, 리바인 무제한. */
    public static BuyInPolicy defaults() {
        return new BuyInPolicy(400, 2000, Duration.ofMinutes(10), 3, 0);
    }
}
