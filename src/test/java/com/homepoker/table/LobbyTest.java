package com.homepoker.table;

import com.homepoker.equity.EquityService;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import com.homepoker.web.dto.LobbyRow;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyTest {

    private static TableService newService() {
        return new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(),
                new StatsService());
    }

    @Test
    void lobbyListsAllTablesSortedById() {
        TableService service = newService();
        service.join("zulu", "a", "A", 1000);
        service.join("alpha", "b", "B", 1000);

        List<LobbyRow> lobby = service.lobby();
        assertEquals(2, lobby.size());
        assertEquals("alpha", lobby.get(0).tableId()); // id 오름차순
        assertEquals("zulu", lobby.get(1).tableId());
    }

    @Test
    void lobbyReflectsSeatCountAndHandState() {
        TableService service = newService();
        service.join("t1", "alice", "Alice", 1000);
        service.join("t1", "bob", "Bob", 1000);

        LobbyRow before = service.lobby().get(0);
        assertEquals(2, before.seatedCount());
        assertFalse(before.handInProgress());
        assertEquals(0, before.handsPlayed());

        service.startHand("t1");
        LobbyRow during = service.lobby().get(0);
        assertTrue(during.handInProgress());
        assertEquals(1, during.handsPlayed());
        assertEquals(10, during.smallBlind());
        assertEquals(20, during.bigBlind());
    }

    @Test
    void emptyLobbyWhenNoTables() {
        assertTrue(newService().lobby().isEmpty());
    }
}
