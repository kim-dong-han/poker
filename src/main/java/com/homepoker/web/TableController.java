package com.homepoker.web;

import com.homepoker.rule.RuleViolation;
import com.homepoker.table.TableService;
import com.homepoker.web.dto.ActionRequest;
import com.homepoker.web.dto.JoinRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP 메시지 진입점. 액션의 주체는 항상 세션 principal(playerId)로 못박아
 * 남의 이름으로 액션하는 것을 원천 차단한다.
 *
 * 상태 브로드캐스트: 홀카드 리댁션이 사람마다 다르므로 단일 /topic 이 아니라
 * 착석자 각자에게 개인화된 뷰를 /user/queue/table.{id} 로 보낸다.
 */
@Controller
public class TableController {

    private final TableService tableService;
    private final ViewBroadcaster broadcaster;

    public TableController(TableService tableService, ViewBroadcaster broadcaster) {
        this.tableService = tableService;
        this.broadcaster = broadcaster;
    }

    @MessageMapping("/table/{id}/join")
    public void join(@DestinationVariable String id, JoinRequest req, Principal principal) {
        tableService.join(id, principal.getName(), req.name(), req.buyIn());
        broadcast(id);
    }

    /** 버스트 후 리바인 — 쿨다운 없이 즉시 재착석(횟수 한도는 RuleGuard 정책). */
    @MessageMapping("/table/{id}/rebuy")
    public void rebuy(@DestinationVariable String id, JoinRequest req, Principal principal) {
        tableService.rebuy(id, principal.getName(), req.name(), req.buyIn());
        broadcast(id);
    }

    @MessageMapping("/table/{id}/start")
    public void start(@DestinationVariable String id) {
        tableService.startHand(id);
        broadcast(id);
    }

    @MessageMapping("/table/{id}/action")
    public void action(@DestinationVariable String id, ActionRequest req, Principal principal) {
        tableService.applyAction(id, principal.getName(), req.type(), req.amount());
        broadcast(id);
    }

    /**
     * 규칙 위반(차례 아님·최소 레이즈 미만 등 게임 규칙, 또는 바이인·쿨다운 등 RuleGuard 정책)은
     * 요청자에게만 사유를 돌려준다.
     */
    @MessageExceptionHandler({
            IllegalStateException.class, IllegalArgumentException.class, RuleViolation.class})
    @SendToUser("/queue/errors")
    public String handleError(RuntimeException ex) {
        return ex.getMessage();
    }

    private void broadcast(String tableId) {
        broadcaster.broadcast(tableId);
    }
}
