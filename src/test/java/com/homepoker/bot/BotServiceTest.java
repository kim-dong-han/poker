package com.homepoker.bot;

import com.homepoker.engine.game.HandEngine;
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
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    // 봇 액션마다 판단 근거가 남고, 진행 중 핸드의 것은 god=true 없이는 보이지 않는다.
    @Test
    void reasonsRecordedAndCurrentHandHiddenUntilComplete() {
        TableService tableService = newTableService();
        BotService botService = newBotService(tableService);
        botService.addBot("t1");
        botService.addBot("t1");
        tableService.startHand("t1");

        Table table = tableService.getOrCreate("t1");
        int guard = 0;
        while (table.handInProgress()) {
            if (guard++ > 300) {
                throw new AssertionError("봇 핸드가 끝나지 않음");
            }
            boolean acted = botService.actIfBotTurn("t1");
            if (acted && table.handInProgress()) {
                assertTrue(botService.reasons("t1", false).isEmpty(),
                        "진행 중 핸드의 판단 근거는 숨겨야 함(봇 핸드 강도 유출)");
                assertFalse(botService.reasons("t1", true).isEmpty(),
                        "전지적(god) 요청이면 진행 중에도 보임");
            }
        }
        var all = botService.reasons("t1", false);
        assertFalse(all.isEmpty(), "핸드 종료 후에는 전부 공개");
        assertTrue(all.stream().allMatch(a -> a.reason() != null && !a.reason().isBlank()));
        assertTrue(all.stream().allMatch(a -> a.handNo() == 1));
    }

    // 두뇌가 예외를 던져도 테이블이 멈추지 않는다 — 안전 액션(체크/폴드)으로 강제 진행.
    // (스크린샷 55 동결 버그의 방어: "AI 생각 중" 무한 루프 금지)
    @Test
    void failSafeActionWhenBrainThrows() {
        TableService tableService = newTableService();
        BotBrain broken = new BotBrain(new EquityService()) {
            @Override
            public Decision decide(HandEngine engine, String botId) {
                throw new IllegalStateException("두뇌 고장(테스트)");
            }
        };
        BotService botService = new BotService(tableService, broken, 0);
        botService.addBot("t1");
        botService.addBot("t1");
        tableService.startHand("t1");

        Table table = tableService.getOrCreate("t1");
        int guard = 0;
        while (table.handInProgress()) {
            if (guard++ > 50) {
                throw new AssertionError("두뇌 고장에도 핸드는 안전 액션으로 끝까지 진행돼야 한다");
            }
            botService.actIfBotTurn("t1");
        }
        long total = table.seatedPlayers().stream().mapToLong(Player::stack).sum();
        assertEquals(2000, total, "칩 보존");
    }

    // 두뇌가 불법 액션(스택 초과 레이즈 등)을 내놔도 안전 액션으로 대체해 진행한다.
    @Test
    void failSafeActionWhenBrainReturnsIllegalAction() {
        TableService tableService = newTableService();
        BotBrain badAmount = new BotBrain(new EquityService()) {
            @Override
            public Decision decide(HandEngine engine, String botId) {
                return new Decision("RAISE", 999_999, "불법 금액(테스트)");
            }
        };
        BotService botService = new BotService(tableService, badAmount, 0);
        botService.addBot("t1");
        botService.addBot("t1");
        tableService.startHand("t1");

        Table table = tableService.getOrCreate("t1");
        int guard = 0;
        while (table.handInProgress()) {
            if (guard++ > 50) {
                throw new AssertionError("불법 액션에도 핸드는 안전 액션으로 끝까지 진행돼야 한다");
            }
            botService.actIfBotTurn("t1");
        }
        long total = table.seatedPlayers().stream().mapToLong(Player::stack).sum();
        assertEquals(2000, total, "칩 보존");
    }

    // 두뇌가 응답하지 않아도(무한 대기) 판단 제한시간이 끊고 안전 액션으로 진행한다.
    @Test
    void safeActionWhenBrainHangs() {
        TableService tableService = newTableService();
        BotBrain hanging = new BotBrain(new EquityService()) {
            @Override
            public Decision decide(HandEngine engine, String botId) {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); // 제한시간 초과 시 인터럽트로 회수된다
                }
                return new Decision("CHECK", 0, "너무 늦은 판단");
            }
        };
        BotService botService = new BotService(tableService, hanging, 0, 200); // 판단 제한 0.2초
        botService.addBot("t1");
        botService.addBot("t1");
        tableService.startHand("t1");

        Table table = tableService.getOrCreate("t1");
        int guard = 0;
        while (table.handInProgress()) {
            if (guard++ > 20) {
                throw new AssertionError("두뇌가 멈춰도 안전 액션으로 핸드가 끝까지 진행돼야 한다");
            }
            botService.actIfBotTurn("t1");
        }
        long total = table.seatedPlayers().stream().mapToLong(Player::stack).sum();
        assertEquals(2000, total, "칩 보존");
    }

    // 핵심 동결 회귀: 봇이 "생각 중"(판단이 안 끝남)이어도 타임아웃 자동 액션은
    // 테이블 락에 막히지 않고 즉시 들어가야 한다(스크린샷 55·56 — 무한 "생각 중" 동결).
    @Test
    void timeoutAutoActionIsNotBlockedByThinkingBot() throws Exception {
        TableService tableService = new TableService(
                new RuleGuard(BuyInPolicy.defaults(), Clock.systemDefaultZone()),
                new EquityService(),
                new StatsService(),
                new TurnTimer(Clock.systemDefaultZone(), Duration.ofMillis(150))); // 짧은 타임뱅크
        CountDownLatch thinking = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BotBrain stuck = new BotBrain(new EquityService()) {
            @Override
            public Decision decide(HandEngine engine, String botId) {
                thinking.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return new Decision("CHECK", 0, "늦은 판단");
            }
        };
        // 판단 제한시간을 일부러 길게(30초) — "생각이 끝나지 않는" 상황을 재현
        BotService botService = new BotService(tableService, stuck, 0, 30_000);
        tableService.join("t1", "me", "Me", 1000);
        botService.addBot("t1");
        tableService.startHand("t1");
        Table table = tableService.getOrCreate("t1");
        tableService.applyAction("t1", "me", "CALL", 0); // 사람(SB) 콜 → 봇(BB) 차례

        Thread sweeper = new Thread(() -> botService.actIfBotTurn("t1"));
        sweeper.start();
        try {
            assertTrue(thinking.await(2, TimeUnit.SECONDS), "봇이 생각을 시작해야 한다");
            Thread.sleep(250); // 타임뱅크(150ms) 만료 대기

            // 판단이 테이블 락을 쥔 채라면 여기서 영원히 블록된다 — 2초 안에 끝나야 통과
            Boolean acted = CompletableFuture
                    .supplyAsync(() -> tableService.enforceTimeout("t1"))
                    .get(2, TimeUnit.SECONDS);
            assertTrue(acted, "봇이 생각 중이어도 타임아웃 자동 액션은 즉시 들어가야 한다");
        } finally {
            release.countDown();
            sweeper.join(3000);
        }
        // 뒤늦게 끝난 낡은 판단(프리플랍용 체크)은 액션 수 불일치로 폐기돼야 한다
        assertTrue(botService.reasons("t1", true).isEmpty(),
                "타임아웃이 먼저 액션을 넣었으면 봇의 낡은 판단은 적용되지 않아야 한다");
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
