package com.homepoker.web.dto;

import java.util.List;

/**
 * 한 좌석의 뷰. holeCards 는 리댁션 결과 —
 * 보는 사람 본인이거나 쇼다운 공개 대상일 때만 채워지고, 그 외에는 null(카드 뒷면).
 */
public record SeatView(
        String playerId,
        String name,
        long stack,
        String status,
        long committedThisStreet,
        List<String> holeCards,
        boolean button,
        boolean currentActor
) {
}
