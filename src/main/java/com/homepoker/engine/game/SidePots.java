package com.homepoker.engine.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 각 플레이어의 총 기여액(폴드한 플레이어 포함)으로부터 메인팟 + 사이드팟들을 만든다.
 * 폴드한 플레이어의 칩도 팟에 들어가지만 그 팟을 다툴 자격(eligible)은 없다.
 *
 * 알고리즘: 가장 적게 넣은 기여액을 기준으로 한 겹씩 벗겨내며 팟을 쌓는다.
 * 예) A=100, B=100, C=40(올인) → C가 참가하는 메인팟(40*3=120) + A·B만의 사이드팟(60*2=120).
 */
final class SidePots {

    private SidePots() {
    }

    /**
     * @param contributions 플레이어ID → 이번 핸드 총 기여 칩 (기여 순서 유지 위해 LinkedHashMap 권장)
     * @param folded        폴드한 플레이어ID 집합(팟에는 기여하지만 이길 자격 없음)
     */
    static List<Pot> build(Map<String, Long> contributions, Set<String> folded) {
        // 0 초과 기여만 남긴 가변 복사본
        Map<String, Long> remaining = new LinkedHashMap<>();
        contributions.forEach((id, amt) -> {
            if (amt > 0) {
                remaining.put(id, amt);
            }
        });

        List<Pot> pots = new ArrayList<>();
        while (!remaining.isEmpty()) {
            long level = remaining.values().stream().mapToLong(Long::longValue).min().orElse(0);
            long potAmount = 0;
            List<String> contributors = new ArrayList<>();
            for (Map.Entry<String, Long> e : remaining.entrySet()) {
                potAmount += level;
                contributors.add(e.getKey());
            }
            // 이 겹만큼 차감하고 0이 된 사람은 제거
            remaining.replaceAll((id, amt) -> amt - level);
            remaining.values().removeIf(amt -> amt <= 0);

            List<String> eligible = contributors.stream().filter(id -> !folded.contains(id)).toList();
            if (eligible.isEmpty()) {
                // 이 겹에 자격자가 없다(모두 폴드). 죽은 칩은 직전 팟에 합친다.
                if (!pots.isEmpty()) {
                    Pot prev = pots.get(pots.size() - 1);
                    pots.set(pots.size() - 1, new Pot(prev.amount() + potAmount, prev.eligiblePlayerIds()));
                }
                continue;
            }
            pots.add(new Pot(potAmount, eligible));
        }
        return pots;
    }
}
