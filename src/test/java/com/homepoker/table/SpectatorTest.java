package com.homepoker.table;

import com.homepoker.equity.EquityService;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import com.homepoker.web.dto.SeatView;
import com.homepoker.web.dto.TableStateView;
import org.junit.jupiter.api.Test;

import java.time.Clock;

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
}
