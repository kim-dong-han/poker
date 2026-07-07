package com.homepoker.web;

import com.homepoker.table.TableService;
import com.homepoker.web.dto.LobbyRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 로비 REST 엔드포인트. 프론트가 GET /api/tables 로 방 목록을 폴링한다. */
@RestController
public class LobbyController {

    private final TableService tableService;

    public LobbyController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/api/tables")
    public List<LobbyRow> tables() {
        return tableService.lobby();
    }
}
