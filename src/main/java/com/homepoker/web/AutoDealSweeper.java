package com.homepoker.web;

import com.homepoker.table.AutoDealService;
import com.homepoker.table.TableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 종료된 핸드의 대기 시간이 지나면 자동으로 다음 핸드를 시작하고 새 뷰를 브로드캐스트한다.
 * (BotSweeper 와 같은 패턴 — 판단·동시성은 AutoDealService, 여기선 주기 호출+전송만.)
 */
@Component
public class AutoDealSweeper {

    private static final Logger log = LoggerFactory.getLogger(AutoDealSweeper.class);

    private final TableService tableService;
    private final AutoDealService autoDealService;
    private final ViewBroadcaster broadcaster;

    public AutoDealSweeper(TableService tableService, AutoDealService autoDealService,
                           ViewBroadcaster broadcaster) {
        this.tableService = tableService;
        this.autoDealService = autoDealService;
        this.broadcaster = broadcaster;
    }

    @Scheduled(fixedDelay = 500)
    public void sweep() {
        for (String tableId : tableService.activeTableIds()) {
            try {
                if (autoDealService.dealIfDue(tableId)) {
                    broadcaster.broadcast(tableId);
                }
            } catch (RuntimeException ex) {
                // 좌석 이탈 등으로 시작 조건이 그 사이 깨졌을 수 있다 — 다음 주기에 재평가.
                log.warn("자동 다음 핸드 시작 실패(table={}): {}", tableId, ex.toString());
            }
        }
    }
}
