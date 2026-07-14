package com.homepoker.web;

import com.homepoker.table.TableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1초마다 모든 테이블을 훑어 제한시간이 지난 액션자에게 자동 액션을 넣고, 넣었으면
 * 모두에게 새 뷰를 브로드캐스트한다(안 하면 클라이언트는 계속 이전 차례 화면에 멈춰 보인다 —
 * 스크린샷 55 버그의 관찰 증상). 실제 자동 액션 로직·동시성 처리는 TableService.enforceTimeout,
 * 여기선 주기 호출+전송만(BotSweeper 와 같은 패턴이라 web 패키지에 둔다).
 */
@Component
public class TurnTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(TurnTimeoutSweeper.class);

    private final TableService tableService;
    private final ViewBroadcaster broadcaster;

    public TurnTimeoutSweeper(TableService tableService, ViewBroadcaster broadcaster) {
        this.tableService = tableService;
        this.broadcaster = broadcaster;
    }

    @Scheduled(fixedDelay = 1000)
    public void sweep() {
        for (String tableId : tableService.activeTableIds()) {
            try {
                if (tableService.enforceTimeout(tableId)) {
                    broadcaster.broadcast(tableId);
                }
            } catch (RuntimeException ex) {
                // 경합으로 이미 다른 경로가 액션을 넣었을 수 있다 — 다음 주기에 재시도.
                // 단, 반복되는 실패를 조용히 삼키면 테이블 멈춤을 진단할 수 없으므로 로그는 남긴다.
                log.warn("타임아웃 자동 액션 실패(table={}): {}", tableId, ex.toString());
            }
        }
    }
}
