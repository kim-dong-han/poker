package com.homepoker.web;

import com.homepoker.table.TableService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 착석자와 현재 핸드 참가자 전원에게 각자의 리댁션 뷰를 전송한다.
 * STOMP 진입점(TableController)과 서버 주도 액션(봇 스위퍼)이 공유하는 유일한 브로드캐스트 경로.
 * AI 좌석은 세션이 없어 전송이 조용히 버려진다(무해).
 */
@Component
public class ViewBroadcaster {

    private final TableService tableService;
    private final SimpMessagingTemplate messaging;

    public ViewBroadcaster(TableService tableService, SimpMessagingTemplate messaging) {
        this.tableService = tableService;
        this.messaging = messaging;
    }

    public void broadcast(String tableId) {
        // 착석자 ∪ 핸드 참가자 — 버스트로 좌석이 비워진 패자도 종료 화면은 받아야 한다
        for (String playerId : tableService.broadcastTargetIds(tableId)) {
            messaging.convertAndSendToUser(
                    playerId,
                    "/queue/table." + tableId,
                    tableService.viewFor(tableId, playerId));
        }
    }
}
