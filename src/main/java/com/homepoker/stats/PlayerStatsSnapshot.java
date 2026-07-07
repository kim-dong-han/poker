package com.homepoker.stats;

/**
 * PlayerStats 의 영속용 평면 스냅샷. 도메인 객체에 Jackson 을 묻히지 않으려고 분리했다
 * (직렬화는 이 record 만 안다). 파일에 저장되는 단위.
 */
public record PlayerStatsSnapshot(
        String playerId,
        String name,
        int handsPlayed,
        int vpipHands,
        int pfrHands,
        int handsWon,
        long netProfit
) {
}
