package com.homepoker.range;

import com.homepoker.engine.card.Card;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 6-max 100bb 표준 프리플랍 RFI(오픈) 레인지를 정적 JSON 에서 읽어 조회를 제공한다.
 * 이퀴티처럼 "얇은 읽기전용 분석 오버레이" — 게임 진행에 개입하지 않고, 히어로 핸드가
 * 해당 포지션의 표준 오픈에 드는지 알려줄 뿐이다.
 */
@Service
public class PreflopRangeService {

    private static final String RESOURCE = "preflop/rfi-6max-100bb.json";

    private final Map<Position, Set<String>> ranges;

    public PreflopRangeService() {
        this.ranges = load(RESOURCE);
    }

    private Map<Position, Set<String>> load(String resource) {
        ObjectMapper mapper = JsonMapper.builder().build();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("프리플랍 레인지 리소스를 찾을 수 없다: " + resource);
            }
            Map<String, List<String>> raw = mapper.readValue(in, new TypeReference<>() {});
            EnumMap<Position, Set<String>> out = new EnumMap<>(Position.class);
            for (Position p : Position.values()) {
                out.put(p, Set.copyOf(raw.getOrDefault(p.name(), List.of())));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("프리플랍 레인지 로드 실패: " + resource, e);
        }
    }

    /** 해당 포지션의 표준 오픈 핸드 집합(표기 문자열). */
    public Set<String> openingRange(Position position) {
        return ranges.get(position);
    }

    public Map<Position, Set<String>> all() {
        return ranges;
    }

    /** 표기(예: "AJs")가 그 포지션 오픈 레인지에 드는가. */
    public boolean shouldOpen(Position position, String hand) {
        return ranges.get(position).contains(hand);
    }

    /** 히어로 홀카드 2장이 그 포지션 오픈 레인지에 드는가(오버레이용). */
    public boolean shouldOpen(Position position, List<Card> hole) {
        return shouldOpen(position, HandNotation.of(hole));
    }
}
