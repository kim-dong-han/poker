package com.homepoker.web;

import com.homepoker.table.ReplayService;
import com.homepoker.web.dto.HandSummaryView;
import com.homepoker.web.dto.ReplayFrame;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 핸드 히스토리 리플레이 REST 엔드포인트.
 *  - GET /api/tables/{id}/hands           : 완료 핸드 목록(최신순)
 *  - GET /api/tables/{id}/hands/{index}   : 그 핸드를 프레임 단위로 되감기
 */
@RestController
public class ReplayController {

    private final ReplayService replayService;

    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }

    @GetMapping("/api/tables/{id}/hands")
    public List<HandSummaryView> hands(@PathVariable String id) {
        return replayService.summaries(id);
    }

    @GetMapping("/api/tables/{id}/hands/{index}")
    public List<ReplayFrame> replay(@PathVariable String id, @PathVariable int index) {
        return replayService.frames(id, index);
    }
}
