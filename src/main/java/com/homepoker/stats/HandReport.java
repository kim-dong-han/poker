package com.homepoker.stats;

import java.util.Map;
import java.util.Set;

/**
 * 한 핸드가 끝났을 때 StatsService 에 넘기는 요약. TableService 가 핸드 진행 중 모아서 만든다.
 *
 * @param names            playerId → 표시 이름
 * @param dealt            이 핸드에 참여한(카드 받은) 플레이어들
 * @param voluntaryPreflop 프리플랍에 자발적으로 콜/벳/레이즈한 플레이어들(VPIP)
 * @param preflopRaisers   프리플랍에 레이즈한 플레이어들(PFR)
 * @param netDelta         playerId → 이 핸드 순손익(종료스택 - 시작스택)
 * @param winners          팟을 획득한 플레이어들
 */
public record HandReport(
        Map<String, String> names,
        Set<String> dealt,
        Set<String> voluntaryPreflop,
        Set<String> preflopRaisers,
        Map<String, Long> netDelta,
        Set<String> winners
) {
}
