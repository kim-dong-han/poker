package com.homepoker.web.dto;

/** 리더보드 한 줄. vpip/pfr 는 백분율(정수). */
public record LeaderboardRow(
        String playerId,
        String name,
        int handsPlayed,
        int handsWon,
        long netProfit,
        int vpip,
        int pfr
) {
}
