package com.homepoker.web;

import com.homepoker.fairness.ShuffleProof;
import com.homepoker.table.Table;
import com.homepoker.table.TableService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 검증 가능한 셔플(commit-reveal) 조회 엔드포인트.
 *  - GET /api/tables/{id}/fairness : 현재 핸드의 커밋 해시 + 완료 핸드들의 셔플 증명
 *
 * 증명(솔트+덱 전체)은 완료 핸드 것만 담기므로 진행 중 카드 정보는 절대 유출되지 않는다.
 * proofs 의 인덱스는 /api/tables/{id}/hands 의 핸드 인덱스와 동일하다(최신이 0).
 */
@RestController
public class FairnessController {

    /** 응답 뷰. currentCommitment 는 핸드 시작 전이면 null. */
    public record FairnessView(String currentCommitment, boolean handInProgress,
                               List<ShuffleProof> proofs) {
    }

    private final TableService tableService;

    public FairnessController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/api/tables/{id}/fairness")
    public FairnessView fairness(@PathVariable String id) {
        Table table = tableService.getOrCreate(id);
        return new FairnessView(table.shuffleCommitment(), table.handInProgress(),
                table.shuffleProofs());
    }
}
