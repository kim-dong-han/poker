package com.homepoker.web;

import com.homepoker.bot.BotService;
import com.homepoker.table.TableService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 봇 차례를 주기적으로 확인해 대신 액션을 넣고, 넣었으면 모두에게 새 뷰를 브로드캐스트한다.
 * (TurnTimeoutSweeper 와 같은 패턴 — 판단·동시성은 BotService, 여기선 주기 호출+전송만.)
 */
@Component
public class BotSweeper {

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
            } catch (RuntimeException ignore) {
                // 사람 액션·타임아웃과의 경합으로 차례가 이미 지나갔을 수 있다 — 다음 주기에 재시도.
            }
        }
    }
}
