package com.homepoker.web;

import com.homepoker.bot.BotService;
import com.homepoker.table.TableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 봇 차례를 주기적으로 확인해 대신 액션을 넣고, 넣었으면 모두에게 새 뷰를 브로드캐스트한다.
 * (TurnTimeoutSweeper 와 같은 패턴 — 판단·동시성은 BotService, 여기선 주기 호출+전송만.)
 */
@Component
public class BotSweeper {

    private static final Logger log = LoggerFactory.getLogger(BotSweeper.class);

    private final TableService tableService;
    private final BotService botService;
    private final ViewBroadcaster broadcaster;

    public BotSweeper(TableService tableService, BotService botService, ViewBroadcaster broadcaster) {
        this.tableService = tableService;
        this.botService = botService;
        this.broadcaster = broadcaster;
    }

    @Scheduled(fixedDelay = 400)
    public void sweep() {
        for (String tableId : tableService.activeTableIds()) {
            try {
                if (botService.actIfBotTurn(tableId)) {
                    broadcaster.broadcast(tableId);
                }
            } catch (RuntimeException ex) {
                // 사람 액션·타임아웃과의 경합으로 차례가 이미 지나갔을 수 있다 — 다음 주기에 재시도.
                // 단, 반복 실패를 조용히 삼키면 "AI 생각 중" 멈춤을 진단할 수 없으므로 로그는 남긴다.
                log.warn("봇 스위프 실패(table={}): {}", tableId, ex.toString());
            }
        }
    }
}
