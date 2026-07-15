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
        boolean currentActor,
        /** 이번 스트리트에 이 플레이어가 한 마지막 액션("CHECK"/"CALL"/"BET 60"...). 없으면 null. */
        String lastAction,
        /** 홀카드가 공개된 좌석의 족보 라벨("투 페어 A·9" 등). 미공개·프리플랍이면 null. */
        String handLabel
) {
}
