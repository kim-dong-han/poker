package com.homepoker.range;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 프리플랍 전략 차트(포지션·상황별 액션 빈도) 로더.
 *
 * 데이터 파일(preflop/bts-preflop.json)은 유료 교육 자료(BluffTheSpot Preflop Bible)를
 * 개인 소장 PDF 에서 전사한 것이라 저작권상 레포에 커밋하지 않는다(.gitignore).
 * 파일이 없으면 available()=false 로 조용히 비활성화되고, 봇은 기존 이퀴티 로직으로만 판단한다.
 *
 * 구조: 섹션(openRaise/threeBet/bbDefense/fourBet/fiveBet/squeeze/...) → 차트 키(포지션 매치업)
 *  → 핸드 표기("AJs") → 액션별 콤보 비율(0~1). 셀이 반씩 칠해진 혼합 핸드는 비율로 표현된다.
 */
@Service
public class BtsPreflopCharts {

    public static final String RESOURCE = "preflop/bts-preflop.json";

    /** 차트 데이터가 아닌 메타 키(액션 조회에서 제외). */
    private static final java.util.Set<String> META_KEYS = java.util.Set.of("sizing", "source", "note");

    private final JsonNode root; // null 이면 비활성

    public BtsPreflopCharts() {
        this(RESOURCE);
    }

    public BtsPreflopCharts(String resource) {
        JsonNode loaded = null;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
                loaded = JsonMapper.builder().build().readTree(in);
            }
        } catch (Exception e) {
            loaded = null; // 손상된 파일도 비활성 처리 — 게임 진행을 막지 않는다
        }
        this.root = loaded;
    }

    private BtsPreflopCharts(JsonNode root) {
        this.root = root;
    }

    /** 차트 없이 동작하는 인스턴스(테스트·차트 미보유 환경용). */
    public static BtsPreflopCharts empty() {
        return new BtsPreflopCharts((JsonNode) null);
    }

    public boolean available() {
        return root != null;
    }

    public boolean hasChart(String section, String chartKey) {
        return root != null && !META_KEYS.contains(section)
                && root.path(section).path(chartKey).isObject();
    }

    /**
     * 해당 상황·핸드의 액션별 콤보 비율. 차트에 없는 핸드는 빈 맵(= 그 상황의 기본 액션, 보통 폴드).
     */
    public Map<String, Double> actions(String section, String chartKey, String hand) {
        if (!hasChart(section, chartKey)) {
            return Map.of();
        }
        JsonNode node = root.path(section).path(chartKey).path(hand);
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, Double> out = new HashMap<>();
        node.properties().forEach(e -> out.put(e.getKey(), e.getValue().asDouble()));
        return out;
    }

    /** 포지션별 오픈 레이즈 사이즈(bb). 차트 없으면 2.5bb. */
    public double openRaiseBB(String position) {
        if (root == null) {
            return 2.5;
        }
        JsonNode n = root.path("sizing").path("openRaiseBB").path(position);
        return n.isNumber() ? n.asDouble() : 2.5;
    }

    /** 3벳 도착 금액(bb): 오프너×3벳터 매트릭스. 없으면 IP 7bb / OOP 10.5bb 근사. */
    public double threeBetToBB(String openerPos, String threeBetterPos) {
        if (root != null) {
            JsonNode n = root.path("sizing").path("threeBetToBB").path(openerPos).path(threeBetterPos);
            if (n.isNumber()) {
                return n.asDouble();
            }
        }
        boolean oop = "SB".equals(threeBetterPos) || "BB".equals(threeBetterPos);
        return oop ? 10.5 : 7;
    }

    /** 4벳 도착 금액(bb): IP 20 / 블라인드 25 / SB vs BB 23. */
    public double fourBetToBB(boolean threeBetterInBlinds, boolean sbVsBb) {
        if (sbVsBb) {
            return 23;
        }
        return threeBetterInBlinds ? 25 : 20;
    }
}
