package com.homepoker.bot;

import com.homepoker.engine.game.Player;
import com.homepoker.equity.EquityService;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import com.homepoker.table.Table;
import com.homepoker.table.TableService;
import com.homepoker.table.TurnTimer;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotServiceTest {

    private static TableService newTableService() {
        return new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(),
                new StatsService(),
                new TurnTimer(Clock.systemDefaultZone()));
    }

    /** 생각 지연 0 인 봇 서비스(테스트가 스위퍼 역할). */
    private static BotService newBotService(TableService tableService) {
        return new BotService(tableService, new BotBrain(new EquityService()), 0);
    }

    // 봇 2명만 앉혀 시작하면 사람 개입 없이 핸드가 끝까지 진행되고 칩이 보존된다.
    @Test
    void twoBotsPlayFullHandToCompletion() {
        TableService tableService = newTableService();
        BotService botService = newBotService(tableService);

        assertEquals("ai-1", botService.addBot("t1"));
        assertEquals("ai-2", botService.addBot("t1"));
        tableService.startHand("t1");

        Table table = tableService.getOrCreate("t1");
        int guard = 0;
        while (table.handInProgress()) {
            if (guard++ > 300) {
                throw new AssertionError("봇 핸드가 끝나지 않음");
            }
            botService.actIfBotTurn("t1");
        }
        long total = table.engine().players().stream().mapToLong(Player::stack).sum();
        assertEquals(2000, total); // 칩 보존
        assertEquals(1, table.history().size()); // 히스토리 보관 → 복기도 가능
    }

    // 사람 차례에는 봇이 절대 대신 액션하지 않는다.
    @Test
    void botNeverActsOnHumanTurn() {
        TableService tableService = newTableService();
        BotService botService = newBotService(tableService);
        tableService.join("t1", "me", "Me", 1000);
        botService.addBot("t1");
        tableService.startHand("t1");

        Table table = tableService.getOrCreate("t1");
        // 봇 차례면 소진시키고, 사람 차례가 되면 false 만 나와야 한다
        int guard = 0;
        while (botService.isBot(table.engine().playerToAct().id())) {
            if (guard++ > 50) {
                throw new AssertionError("사람 차례가 오지 않음");
            }
            botService.actIfBotTurn("t1");
            if (!table.handInProgress()) {
                return; // 봇이 폴드해 바로 끝났으면 그 자체로 통과(사람 대신 액션 없음)
            }
        }
        String humanToAct = table.engine().playerToAct().id();
        assertEquals("me", humanToAct);
        assertFalse(botService.actIfBotTurn("t1")); // 사람 차례 — 아무 것도 하지 않음
        assertEquals("me", table.engine().playerToAct().id());
    }

    // 핸드 진행 중엔 AI 제거 불가, 종료 후엔 가능.
    @Test
    void removeBotOnlyBetweenHands() {
        TableService tableService = newTableService();
        BotService botService = newBotService(tableService);
        botService.addBot("t1");
        botService.addBot("t1");
        tableService.startHand("t1");

        assertThrows(IllegalStateException.class, () -> botService.removeBot("t1"));

        Table table = tableService.getOrCreate("t1");
        int guard = 0;
        while (table.handInProgress() && guard++ <= 300) {
            botService.actIfBotTurn("t1");
        }
        // 올인 승부로 한쪽이 버스트해 이미 자리를 비웠을 수 있다 — 착석 중인 봇 기준으로 검증
        String removed = botService.removeBot("t1");
        assertTrue(removed.startsWith(BotService.ID_PREFIX));
        assertFalse(table.isSeated(removed));
    }
}
