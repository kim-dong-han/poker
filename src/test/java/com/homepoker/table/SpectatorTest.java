package com.homepoker.table;

import com.homepoker.equity.EquityService;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import com.homepoker.web.dto.SeatView;
import com.homepoker.web.dto.TableStateView;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectatorTest {

    private static TableService newService() {
        return new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(),
                new StatsService(),
                new TurnTimer(Clock.systemDefaultZone()));
    }

    @Test
    void spectatorSeesNoHoleCardsAndCannotAct() {
        TableService service = newService();
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        service.startHand("t1");

        TableStateView view = service.spectate("t1");
        assertTrue(view.handInProgress());
        // 진행 중 어떤 좌석의 홀카드도 보이지 않는다
        for (SeatView s : view.seats()) {
            assertNull(s.holeCards(), s.playerId() + " 카드가 관전자에게 노출됨");
        }
        // 관전자는 액션 권한도, 이퀴티도 없다
        assertTrue(view.viewerLegalActions().isEmpty());
        assertNull(view.viewerEquity());
    }

    @Test
    void spectatorStillSeesPublicBoardAndPot() {
        TableService service = newService();
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        service.startHand("t1");
        // 블라인드가 들어갔으므로 팟은 0보다 크다(공개 정보)
        assertTrue(service.spectate("t1").pot() > 0);
    }

    // 전지적 관찰자 뷰는 진행 중에도 폴드 포함 모든 홀카드를 공개한다(명시적 버튼용).
    @Test
    void godViewRevealsAllHoleCardsEvenAfterFold() {
        TableService service = newService();
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);
        service.startHand("t1");
        // 프리플랍 첫 액션자(헤즈업 SB)가 폴드해도 그 카드까지 보인다
        String actor = service.viewFor("t1", "alice").currentActorId();
        service.applyAction("t1", actor, "FOLD", 0);
        service.startHand("t1"); // 새 핸드 진행 중 상태로 확인

        TableStateView god = service.godView("t1");
        assertTrue(god.handInProgress());
        for (SeatView s : god.seats()) {
            assertEquals(2, s.holeCards().size(), s.playerId() + " 카드가 전지적 뷰에 없음");
        }
        // 같은 순간 일반 관전 뷰는 여전히 전부 리댁션 — 전지적 뷰가 기본 경로를 오염시키지 않는다
        for (SeatView s : service.spectate("t1").seats()) {
            assertNull(s.holeCards());
        }
    }
}
