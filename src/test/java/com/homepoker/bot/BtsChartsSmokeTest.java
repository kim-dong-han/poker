package com.homepoker.bot;

import com.homepoker.engine.game.Player;
import com.homepoker.equity.EquityService;
import com.homepoker.range.BtsPreflopCharts;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import com.homepoker.table.Table;
import com.homepoker.table.TableService;
import com.homepoker.table.TurnTimer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 전사 차트(bts-preflop.json)가 있는 로컬 환경에서만 도는 스모크 테스트.
 * 차트 파일은 저작권상 레포에 없으므로(CI 등) 그 환경에선 자동 스킵된다.
 */
@EnabledIf("chartsPresent")
class BtsChartsSmokeTest {

    static boolean chartsPresent() {
        return new BtsPreflopCharts().available();
    }

    @Test
    void botsPlayFullHandUsingRealChartsAndExplainWithChartReasons() {
        TableService tableService = new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(),
                new StatsService(),
                new TurnTimer(Clock.systemDefaultZone()));
        BotBrain brain = new BotBrain(new EquityService(),
                new PreflopAdvisor(new BtsPreflopCharts(), 0.35));
        BotService botService = new BotService(tableService, brain, 0);

        botService.addBot("t1");
        botService.addBot("t1");
        tableService.startHand("t1");

        Table table = tableService.getOrCreate("t1");
        int guard = 0;
        while (table.handInProgress()) {
            if (guard++ > 300) {
                throw new AssertionError("차트 봇 핸드가 끝나지 않음");
            }
            botService.actIfBotTurn("t1");
        }
        long total = table.engine().players().stream().mapToLong(Player::stack).sum();
        assertEquals(2000, total, "칩 보존");

        var reasons = botService.reasons("t1", true);
        assertFalse(reasons.isEmpty());
        // 헤즈업 프리플랍 첫 결정(SB 오픈/폴드)은 항상 차트가 커버한다
        assertTrue(reasons.get(0).reason().startsWith("차트:"),
                "첫 프리플랍 판단은 차트 근거여야 함: " + reasons.get(0).reason());
    }

    @Test
    void realChartLoadsAndCoversKnownRanges() {
        BtsPreflopCharts charts = new BtsPreflopCharts();
        assertEquals(1.0, charts.actions("openRaise", "UTG", "AA").get("open"), 1e-9);
        assertTrue(charts.hasChart("bbDefense", "BB_vs_SB"));
        assertTrue(charts.hasChart("fiveBet", "BB3bet_vs_SB4bet"));
        assertEquals(3.0, charts.openRaiseBB("SB"), 1e-9);
        assertEquals(9.0, charts.threeBetToBB("SB", "BB"), 1e-9);
    }
}
