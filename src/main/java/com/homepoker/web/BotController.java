package com.homepoker.web;

import com.homepoker.bot.BotService;
import com.homepoker.rule.RuleViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 상대 관리 엔드포인트.
 *  - POST   /api/tables/{id}/bots : AI 한 명 착석 → {botId}
 *  - DELETE /api/tables/{id}/bots : 마지막 AI 제거(핸드 진행 중엔 409)
 *  - GET 은 필요 없음 — 좌석 정보는 테이블 뷰에 이미 있다(ai- 접두사로 식별)
 */
@RestController
public class BotController {

    private final BotService botService;
    private final ViewBroadcaster broadcaster;

    public BotController(BotService botService, ViewBroadcaster broadcaster) {
        this.botService = botService;
        this.broadcaster = broadcaster;
    }

    @PostMapping("/api/tables/{id}/bots")
    public Map<String, Object> add(@PathVariable String id) {
        String botId = botService.addBot(id);
        broadcaster.broadcast(id);
        return Map.of("botId", botId, "bots", botService.bots(id));
    }

    @DeleteMapping("/api/tables/{id}/bots")
    public Map<String, Object> remove(@PathVariable String id) {
        String removed = botService.removeBot(id);
        broadcaster.broadcast(id);
        return Map.of("removed", removed, "bots", botService.bots(id));
    }

    @ExceptionHandler({IllegalStateException.class, RuleViolation.class})
    public ResponseEntity<Map<String, String>> conflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
