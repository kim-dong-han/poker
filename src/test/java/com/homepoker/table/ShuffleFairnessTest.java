package com.homepoker.table;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.game.HandLog;
import com.homepoker.equity.EquityService;
import com.homepoker.fairness.CommittedShuffle;
import com.homepoker.fairness.ShuffleProof;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShuffleFairnessTest {

    private static TableService newTableService() {
        return new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(),
                new StatsService(),
                new TurnTimer(Clock.systemDefaultZone()));
    }

    private static void playOutOneHand(TableService service, String tableId) {
        int guard = 0;
        while (service.viewFor(tableId, "alice").handInProgress()) {
            if (guard++ > 100) {
                throw new AssertionError("핸드가 끝나지 않음");
            }
            String actor = service.viewFor(tableId, "alice").currentActorId();
            var legal = service.viewFor(tableId, actor).viewerLegalActions();
            service.applyAction(tableId, actor, legal.contains("CHECK") ? "CHECK" : "CALL", 0);
        }
    }

    // 진행 중엔 커밋 해시만 공개되고 증명(솔트+덱)은 절대 나오지 않는다.
    @Test
    void commitmentVisibleDuringHandButProofOnlyAfterComplete() {
        TableService service = newTableService();
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        Table table = service.getOrCreate("t1");

        assertEquals(null, table.shuffleCommitment()); // 핸드 전

        service.startHand("t1");
        String commitment = table.shuffleCommitment();
        assertNotNull(commitment);
        assertEquals(64, commitment.length()); // SHA-256 hex
        assertTrue(table.shuffleProofs().isEmpty()); // 진행 중 리빌 금지

        playOutOneHand(service, "t1");
        List<ShuffleProof> proofs = table.shuffleProofs();
        assertEquals(1, proofs.size());
        assertEquals(commitment, proofs.get(0).commitment()); // 시작 전 커밋 그대로
    }

    // 공개된 증명은 해시 재계산으로 검증되고, 리플레이 기록(HandLog)의 초기 덱과 정확히 일치한다.
    @Test
    void proofVerifiesAndMatchesReplayDeck() {
        TableService service = newTableService();
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        service.startHand("t1");
        playOutOneHand(service, "t1");

        Table table = service.getOrCreate("t1");
        ShuffleProof proof = table.shuffleProofs().get(0);
        assertEquals(proof.commitment(),
                CommittedShuffle.commitmentOf(proof.salt(), proof.deckOrder()));

        HandLog log = table.history().get(0);
        assertEquals(proof.deckOrder(), log.deckOrder().stream().map(Card::toString).toList());
    }

    // 증명 목록은 핸드 히스토리와 같은 인덱스(최신이 앞)로 쌓인다.
    @Test
    void proofsAlignWithHistoryIndexes() {
        TableService service = newTableService();
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        service.startHand("t1");
        playOutOneHand(service, "t1");
        service.startHand("t1");
        playOutOneHand(service, "t1");

        Table table = service.getOrCreate("t1");
        assertEquals(2, table.shuffleProofs().size());
        for (int i = 0; i < 2; i++) {
            assertEquals(table.shuffleProofs().get(i).deckOrder(),
                    table.history().get(i).deckOrder().stream().map(Card::toString).toList());
        }
    }
}
