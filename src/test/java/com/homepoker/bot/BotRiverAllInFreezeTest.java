package com.homepoker.bot;

import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.equity.EquityService;
import com.homepoker.rule.BuyInPolicy;
import com.homepoker.rule.RuleGuard;
import com.homepoker.stats.StatsService;
import com.homepoker.table.Table;
import com.homepoker.table.TableService;
import com.homepoker.table.TurnTimer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Set;

/**
 * 재현: 헤즈업에서 봇이 벳 → 사람이 올인 레이즈했을 때 봇이 멈추는 버그(스크린샷 55).
 * 프로덕션과 동일하게 PostflopAdvisor 를 켠 두뇌로 여러 판을 돌려,
 * 사람이 봇의 벳에 올인 레이즈로만 응수해도 핸드가 반드시 끝까지 진행되는지 본다.
 */
class BotRiverAllInFreezeTest {

    private static TableService newTableService() {
        return new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(),
                new StatsService(),
                new TurnTimer(Clock.systemDefaultZone()));
    }

    @Test
    void botNeverStallsWhenHumanShovesOverItsBet() {
        for (int trial = 0; trial < 150; trial++) {
            TableService tableService = newTableService();
            BotBrain brain = new BotBrain(new EquityService(), PreflopAdvisor.disabled(),
                    new PostflopAdvisor(0.7, 0.4, 0.8));
            BotService botService = new BotService(tableService, brain, 0);

            String tid = "t" + trial;
            tableService.join(tid, "kim", "kim", 1000);
            botService.addBot(tid);
            tableService.startHand(tid);

            Table table = tableService.getOrCreate(tid);
            int guard = 0;
            while (table.handInProgress()) {
                if (guard++ > 200) {
                    throw new AssertionError("trial " + trial + ": 핸드가 진행되지 않음(멈춤) — street="
                            + table.engine().street() + ", actor=" + table.engine().playerToAct());
                }
                HandEngine e = table.engine();
                String actor = e.playerToAct().id();
                if (botService.isBot(actor)) {
                    botService.actIfBotTurn(tid); // 예외가 나면 그대로 터뜨려 원인을 본다
                    tableService.viewFor(tid, "kim"); // 브로드캐스트 경로(lastAction 재생 포함)도 통과해야 함
                    continue;
                }
                Set<ActionType> legal = e.legalActions("kim");
                long toCall = e.amountToCall("kim");
                // 봇의 벳/레이즈를 마주하면 무조건 올인 레이즈로 압박(가능할 때)
                if (toCall > 0 && legal.contains(ActionType.RAISE)) {
                    long allInTo = e.committedThisStreet("kim")
                            + table.player("kim").stack();
                    tableService.applyAction(tid, "kim", "RAISE", allInTo);
                } else if (toCall > 0) {
                    tableService.applyAction(tid, "kim", "CALL", 0);
                } else {
                    tableService.applyAction(tid, "kim", "CHECK", 0);
                }
                tableService.viewFor(tid, "kim");
            }
            tableService.viewFor(tid, "kim"); // 핸드 종료 직후 뷰(쇼다운 브로드캐스트)도 예외 없어야 함
        }
    }
}
