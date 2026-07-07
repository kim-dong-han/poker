package com.homepoker.bot;

import com.homepoker.engine.game.Player;
import com.homepoker.table.Table;
import com.homepoker.table.TableService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테이블별 AI 좌석 관리 + 봇 차례가 오면 대신 액션을 넣는다.
 *
 * 봇은 서버 안에서만 존재한다 — WebSocket 세션이 없고, 액션도 STOMP 를 거치지 않고
 * TableService 로 직접 들어간다(타임아웃 자동 액션과 같은 경로). 홀카드 역시 서버 메모리에만
 * 있으므로 사람 클라이언트로는 절대 나가지 않는다(리댁션 규칙 그대로).
 *
 * 동시성: 액션 적용은 Table 모니터로 감싸 사람 액션·타임아웃 스위퍼와의 경합에서 원자성을 보장.
 */
@Service
public class BotService {

    public static final String ID_PREFIX = "ai-";
    private static final long DEFAULT_BUY_IN = 1000;

    private final TableService tableService;
    private final BotBrain brain;
    private final long thinkMillis; // 봇 액션 전 "생각하는" 지연(0이면 즉시 — 테스트용)
    private final Random rng = new Random();

    /** 테이블별 봇 id 집합. */
    private final Map<String, Set<String>> botsByTable = new ConcurrentHashMap<>();
    /** 테이블별 봇이 액션하기로 예약된 시각(epoch ms) — 생각 지연 구현. */
    private final Map<String, Long> actAt = new ConcurrentHashMap<>();

    public BotService(TableService tableService, BotBrain brain,
                      @Value("${poker.bot.think-ms:900}") long thinkMillis) {
        this.tableService = tableService;
        this.brain = brain;
        this.thinkMillis = thinkMillis;
    }

    /** AI 한 명 착석(ai-1, ai-2 …). RuleGuard 바이인 검증은 사람과 동일하게 통과해야 한다. */
    public String addBot(String tableId) {
        Set<String> bots = botsByTable.computeIfAbsent(tableId, k -> ConcurrentHashMap.newKeySet());
        int n = 1;
        while (bots.contains(ID_PREFIX + n) || tableService.getOrCreate(tableId).isSeated(ID_PREFIX + n)) {
            n++;
        }
        String id = ID_PREFIX + n;
        tableService.join(tableId, id, "AI " + n, DEFAULT_BUY_IN);
        bots.add(id);
        return id;
    }

    /** 착석 중인 마지막 AI 제거. 진행 중 핸드에선 거부한다(버스트로 이미 비워진 봇은 명단만 정리). */
    public String removeBot(String tableId) {
        Table table = tableService.getOrCreate(tableId);
        synchronized (table) {
            if (table.handInProgress()) {
                throw new IllegalStateException("핸드 진행 중에는 AI 를 제거할 수 없다(종료 후 다시)");
            }
            cleanupUnseated(tableId, table);
            Set<String> bots = botsByTable.getOrDefault(tableId, Set.of());
            String last = bots.stream().max(String::compareTo)
                    .orElseThrow(() -> new IllegalStateException("제거할 AI 가 없다"));
            table.removeSeat(last);
            bots.remove(last);
            return last;
        }
    }

    public boolean isBot(String playerId) {
        return playerId != null && playerId.startsWith(ID_PREFIX);
    }

    /**
     * 현재 액션자가 이 테이블의 봇이면 (생각 지연 후) 대신 액션을 넣는다.
     * 스위퍼가 주기적으로 호출한다.
     *
     * @return 액션을 실제로 넣었으면 true(호출측이 브로드캐스트)
     */
    public boolean actIfBotTurn(String tableId) {
        Table table = tableService.getOrCreate(tableId);
        synchronized (table) {
            if (!table.handInProgress()) {
                actAt.remove(tableId);
                cleanupUnseated(tableId, table);
                return false;
            }
            Player actor = table.engine().playerToAct();
            if (actor == null || !botsByTable.getOrDefault(tableId, Set.of()).contains(actor.id())) {
                actAt.remove(tableId);
                return false;
            }
            long now = System.currentTimeMillis();
            Long due = actAt.get(tableId);
            if (due == null) {
                // 처음 봇 차례를 본 시점: 사람처럼 잠깐 생각(0.6~1.4배 지터)
                long delay = thinkMillis == 0 ? 0 : (long) (thinkMillis * (0.6 + rng.nextDouble() * 0.8));
                actAt.put(tableId, now + delay);
                if (delay > 0) {
                    return false;
                }
            } else if (now < due) {
                return false;
            }
            actAt.remove(tableId);
            BotBrain.Decision d = brain.decide(table.engine(), actor.id());
            tableService.applyAction(tableId, actor.id(), d.type(), d.amount());
            return true;
        }
    }

    /** 버스트로 자리가 비워진 봇을 명단에서도 정리(재입장 쿨다운은 사람과 동일하게 적용됨). */
    private void cleanupUnseated(String tableId, Table table) {
        Set<String> bots = botsByTable.get(tableId);
        if (bots != null) {
            bots.removeIf(id -> !table.isSeated(id));
        }
    }

    public List<String> bots(String tableId) {
        return List.copyOf(botsByTable.getOrDefault(tableId, Set.of()));
    }
}
