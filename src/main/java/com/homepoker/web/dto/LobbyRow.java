package com.homepoker.web.dto;

/**
 * 로비 목록의 한 줄 = 활성 테이블 하나의 요약. 프론트가 GET /api/tables 로 폴링해 방 목록을 그린다.
 * 홀카드 등 게임 내부 상태는 담지 않는다(로비는 공개 정보만).
 */
public record LobbyRow(
        String tableId,
        long smallBlind,
        long bigBlind,
        int seatedCount,
        boolean handInProgress,
        int handsPlayed
) {
}
