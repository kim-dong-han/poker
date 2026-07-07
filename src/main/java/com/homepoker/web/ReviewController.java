package com.homepoker.web;

import com.homepoker.engine.game.HandLog;
import com.homepoker.review.DecisionReview;
import com.homepoker.review.HandReview;
import com.homepoker.review.HandReviewer;
import com.homepoker.review.SessionReviewRow;
import com.homepoker.table.TableService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 핸드 자동 복기 REST 엔드포인트.
 *  - GET /api/tables/{id}/hands/{index}/review : 그 핸드의 지점별 판정 + 최대 실수
 *  - GET /api/tables/{id}/review/session       : 테이블 전체 핸드 누적 리포트(플레이어별)
 *
 * 복기 결과는 홀카드가 아닌 수치(이퀴티/팟오즈/EV손실)만 담고, 어차피 완료된 핸드
 * (리플레이에서 전 카드 공개)만 다루므로 리댁션 대상이 아니다.
 */
@RestController
public class ReviewController {

    private final TableService tableService;
    private final HandReviewer reviewer;

    /**
     * 복기 결과 캐시(몬테카를로 재계산 방지). history() 는 최신이 index 0 이라 새 핸드가 끝나면
     * 같은 index 가 다른 핸드를 가리키므로, 인덱스가 아니라 불변 HandLog 값 자체를 키로 쓴다.
     */
    private static final int CACHE_LIMIT = 500;
    private final Map<HandLog, HandReview> cache = new ConcurrentHashMap<>();

    public ReviewController(TableService tableService, HandReviewer reviewer) {
        this.tableService = tableService;
        this.reviewer = reviewer;
    }

    @GetMapping("/api/tables/{id}/hands/{index}/review")
    public HandReview review(@PathVariable String id, @PathVariable int index) {
        List<HandLog> history = tableService.getOrCreate(id).history();
        if (index < 0 || index >= history.size()) {
            throw new IllegalArgumentException("없는 핸드 인덱스: " + index);
        }
        return cachedReview(history.get(index));
    }

    @GetMapping("/api/tables/{id}/review/session")
    public List<SessionReviewRow> session(@PathVariable String id) {
        List<HandLog> history = tableService.getOrCreate(id).history();
        Map<String, List<DecisionReview>> byPlayer = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (HandLog log : history) {
            HandReview hr = cachedReview(log);
            for (DecisionReview d : hr.decisions()) {
                byPlayer.computeIfAbsent(d.playerId(), k -> new ArrayList<>()).add(d);
                names.putIfAbsent(d.playerId(), d.playerName());
            }
        }
        List<SessionReviewRow> rows = new ArrayList<>();
        byPlayer.forEach((pid, list) -> {
            List<DecisionReview> mistakes = list.stream().filter(DecisionReview::mistake).toList();
            double totalLoss = mistakes.stream().mapToDouble(DecisionReview::evLossBb).sum();
            String topType = mistakes.stream()
                    .collect(Collectors.groupingBy(d -> d.street() + " " + d.action(),
                            Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            rows.add(new SessionReviewRow(pid, names.get(pid), list.size(), mistakes.size(),
                    totalLoss, topType));
        });
        rows.sort(Comparator.comparingDouble(SessionReviewRow::totalEvLossBb).reversed());
        return rows;
    }

    private HandReview cachedReview(HandLog log) {
        if (cache.size() > CACHE_LIMIT) {
            cache.clear(); // 테이블당 히스토리가 50개로 제한돼 실사용에선 드묾 — 단순 전체 비움으로 충분
        }
        return cache.computeIfAbsent(log, reviewer::review);
    }
}
