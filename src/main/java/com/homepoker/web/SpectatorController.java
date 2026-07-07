package com.homepoker.web;

import com.homepoker.table.TableService;
import com.homepoker.web.dto.TableStateView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관전 모드 REST 엔드포인트. 착석하지 않고 테이블을 구경만 한다 —
 * 진행 중 홀카드는 아무에게도 보이지 않고(쇼다운 공개분만 예외), 액션 권한도 없다.
 */
@RestController
public class SpectatorController {

    private final TableService tableService;

    public SpectatorController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/api/tables/{id}/spectate")
    public TableStateView spectate(@PathVariable String id) {
        return tableService.spectate(id);
    }

    /**
     * 전지적 관찰자 뷰 — 폴드 포함 모든 홀카드 공개(버튼 눌러 명시적으로 볼 때만 호출).
     * 로컬 홈게임 관찰·학습용 기능이라 별도 인증 없음(실서비스 전환 시 제한 필요).
     */
    @GetMapping("/api/tables/{id}/godview")
    public TableStateView godView(@PathVariable String id) {
        return tableService.godView(id);
    }
}
