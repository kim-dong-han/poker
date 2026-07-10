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

    // 9인 테이블에서도 차트가 켜져 있어야 한다(poker_ai_2~4 스크린샷 회귀 — A4s 미오픈 문제).
    // BTS 차트는 UTG 조차 A4s 오픈 1.0 이므로, 9인 UTG 봇의 A4s 는 반드시 레이즈다.
    @Test
    void ninePlayerTableOpensA4sByChart() {
        PreflopAdvisor advisor = new PreflopAdvisor(new BtsPreflopCharts(), 0.35);
        java.util.List<Player> nine = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nine.add(new Player("p" + i, "P" + i, 1000));
        }
        // 버튼 p0 → SB p1, BB p2, UTG p3(첫 액션). 딜은 SB부터: p3 의 카드 = 덱 3번째·12번째
        String[] deck = {
                "2c", "3c", "As", "5c", "6c", "7c", "8c", "9c", "Tc",
                "2d", "3d", "4s", "5d", "6d", "7d", "8d", "9d", "Td",
                "Jh", "Qh", "Kh", "2h", "3h"};
        com.homepoker.engine.card.Deck order = com.homepoker.engine.card.Deck.ofOrder(
                java.util.Arrays.stream(deck).map(com.homepoker.engine.card.Card::of).toList());
        com.homepoker.engine.game.HandEngine e =
                new com.homepoker.engine.game.HandEngine(nine, 0, 10, 20, order);
        e.start();
        var d = advisor.advise(e, "p3", new java.util.Random(1)).orElseThrow();
        assertEquals("RAISE", d.type(), "9인 UTG A4s 는 차트 오픈: " + d.reason());
        assertTrue(d.reason().startsWith("차트:"), d.reason());
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
