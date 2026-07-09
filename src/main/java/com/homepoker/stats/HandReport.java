package com.homepoker.stats;

import java.util.Map;
import java.util.Set;

/**
 * 한 핸드가 끝났을 때 StatsService 에 넘기는 요약. TableService 가 핸드 진행 중 모아서 만든다.
 *
 * @param names             playerId → 표시 이름
 * @param dealt             이 핸드에 참여한(카드 받은) 플레이어들
 * @param voluntaryPreflop  프리플랍에 자발적으로 콜/벳/레이즈한 플레이어들(VPIP)
 * @param preflopRaisers    프리플랍에 레이즈한 플레이어들(PFR)
 * @param netDelta          playerId → 이 핸드 순손익(종료스택 - 시작스택)
 * @param winners           팟을 획득한 플레이어들
 * @param sawFlop           플랍을 본 플레이어들(WtSD 분모)
 * @param postflopAggr      playerId → 포스트플랍 벳/레이즈 횟수(AF 분자)
 * @param postflopCalls     playerId → 포스트플랍 콜 횟수(AF 분모)
 * @param showdown          쇼다운까지 간 플레이어들(WtSD 분자)
 * @param facedThreeBet     프리플랍 오픈 후 3벳을 마주한 플레이어들(F3B 분모)
 * @param foldedToThreeBet  그중 3벳에 폴드한 플레이어들(F3B 분자)
 */
public record HandReport(
        Map<String, String> names,
        Set<String> dealt,
        Set<String> voluntaryPreflop,
        Set<String> preflopRaisers,
        Map<String, Long> netDelta,
        Set<String> winners,
        Set<String> sawFlop,
        Map<String, Integer> postflopAggr,
        Map<String, Integer> postflopCalls,
        Set<String> showdown,
        Set<String> facedThreeBet,
        Set<String> foldedToThreeBet
) {

    /** 포스트플랍 지표 없이 만드는 축약 생성자(기존 테스트·프리플랍만 필요한 경우). */
    public HandReport(Map<String, String> names, Set<String> dealt, Set<String> voluntaryPreflop,
                      Set<String> preflopRaisers, Map<String, Long> netDelta, Set<String> winners) {
        this(names, dealt, voluntaryPreflop, preflopRaisers, netDelta, winners,
                Set.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of());
    }
}
