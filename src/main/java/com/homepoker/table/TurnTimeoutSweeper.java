package com.homepoker.table;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1초마다 모든 테이블을 훑어 제한시간이 지난 액션자에게 자동 액션을 넣는다.
 * 실제 자동 액션 로직·동시성 처리는 TableService.enforceTimeout 에 있다(여기선 주기 호출만).
 */
@Component
public class TurnTimeoutSweeper {

    private final TableService tableService;

    public TurnTimeoutSweeper(TableService tableService) {
        this.tableService = tableService;
    }

    @Scheduled(fixedDelay = 1000)
    public void sweep() {
        for (String tableId : tableService.activeTableIds()) {
            try {
                tableService.enforceTimeout(tableId);
            } catch (RuntimeException ignore) {
                // 경합으로 이미 다른 경로가 액션을 넣었을 수 있다 — 다음 주기에 재시도.
            }
        }
    }
}
