package com.homepoker.bot;

import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandLog;
import com.homepoker.engine.game.Player;
import com.homepoker.table.Table;
import com.homepoker.table.TableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 테이블별 AI 좌석 관리 + 봇 차례가 오면 대신 액션을 넣는다.
 *
 * 봇은 서버 안에서만 존재한다 — WebSocket 세션이 없고, 액션도 STOMP 를 거치지 않고
 * TableService 로 직접 들어간다(타임아웃 자동 액션과 같은 경로). 홀카드 역시 서버 메모리에만
 * 있으므로 사람 클라이언트로는 절대 나가지 않는다(리댁션 규칙 그대로).
 *
 * 동시성: 액션 "적용"만 Table 모니터로 감싸고, 몬테카를로 등 무거운 "판단"은
 * 락 밖의 전용 스레드에서 제한시간(poker.bot.decide-timeout-ms) 안에 돌린다.
 * 그래서 판단이 예외를 던지든, 무한 대기에 빠지든 테이블 락은 절대 잡혀 있지 않아
 * 타임아웃 자동폴드(TableService.enforceTimeout)가 항상 게임을 전진시킬 수 있다 —
 * "AI 생각 중" 상태로 테이블이 영구 동결되는 것을 구조적으로 차단한다.
 */
@Service
public class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);

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

    /**
     * 봇이 실제로 한 액션과 판단 근거(이퀴티 vs 팟오즈). 학습·복기용 기록.
     * reason 에 봇 이퀴티가 드러나므로 진행 중 핸드의 것은 reasons() 에서 기본적으로 걸러낸다.
     */
    public record BotAction(String botId, String name, int handNo, String street,
                            String action, long amount, String reason) {}

    private static final int REASON_LOG_LIMIT = 200;
    /** 테이블별 봇 액션 기록(시간순, 오래된 것부터). 접근은 Table 모니터 안에서만. */
    private final Map<String, Deque<BotAction>> reasonLog = new ConcurrentHashMap<>();

    /** 판단 제한시간 초과·거부 시 대비 스레드 상한 — 둘 다 물려도 게임은 안전 액션으로 계속 간다. */
    private final ExecutorService decidePool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "bot-decide");
        t.setDaemon(true);
        return t;
    });
    private final long decideTimeoutMillis;

    @org.springframework.beans.factory.annotation.Autowired
    public BotService(TableService tableService, BotBrain brain,
                      @Value("${poker.bot.think-ms:900}") long thinkMillis,
                      @Value("${poker.bot.decide-timeout-ms:5000}") long decideTimeoutMillis) {
        this.tableService = tableService;
        this.brain = brain;
        this.thinkMillis = thinkMillis;
        this.decideTimeoutMillis = decideTimeoutMillis;
    }

    /** 판단 제한시간 기본값(5초)을 쓰는 편의 생성자(기존 테스트 호환). */
    public BotService(TableService tableService, BotBrain brain, long thinkMillis) {
        this(tableService, brain, thinkMillis, 5000);
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
        String botId;
        String street;
        int handNo;
        HandLog snapshot;
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
            botId = actor.id();
            street = table.engine().street().name();
            handNo = table.handsPlayed();
            snapshot = table.engine().log(); // 판단은 락 밖에서 이 스냅샷의 복제 엔진으로
        }

        // ---- 테이블 락 밖: 몬테카를로 등 무거운 판단(제한시간 내). 여기서 무엇이 잘못돼도
        //      (예외·무한 대기) 락을 쥐고 있지 않으므로 타임아웃 자동폴드는 절대 막히지 않는다.
        BotBrain.Decision d = decideOffLock(snapshot, botId, tableId, street);

        synchronized (table) {
            // 판단하는 사이 상태가 바뀌었으면(타임아웃 자동 액션·핸드 종료 등) 조용히 물러난다 —
            // 액션 수까지 비교해 "다른 스트리트의 낡은 판단"이 적용되는 일을 막는다
            if (!table.handInProgress()
                    || table.engine().log().actionCount() != snapshot.actionCount()) {
                return false;
            }
            Player actor = table.engine().playerToAct();
            if (actor == null || !actor.id().equals(botId)) {
                return false;
            }
            if (d == null) {
                d = safeDecision(table, botId);
            }
            try {
                tableService.applyAction(tableId, botId, d.type(), d.amount());
            } catch (RuntimeException ex) {
                log.error("봇 액션 거부({} {}) — 안전 액션으로 대체(table={}, bot={}, street={})",
                        d.type(), d.amount(), tableId, botId, street, ex);
                d = safeDecision(table, botId);
                tableService.applyAction(tableId, botId, d.type(), d.amount());
            }
            Deque<BotAction> actions = reasonLog.computeIfAbsent(tableId, k -> new ArrayDeque<>());
            actions.addLast(new BotAction(botId, actor.name(), handNo, street,
                    d.type(), d.amount(), d.reason()));
            while (actions.size() > REASON_LOG_LIMIT) {
                actions.removeFirst();
            }
            return true;
        }
    }

    /**
     * 스냅샷을 복제 엔진으로 되살려 전용 스레드에서 판단한다(제한시간 poker.bot.decide-timeout-ms).
     * 시간 초과·예외·풀 고갈 등 어떤 실패든 null 을 돌려주고(호출측이 안전 액션으로 대체),
     * 원인은 에러 로그로 남긴다 — 판단 실패가 게임 진행을 막는 일은 없다.
     */
    private BotBrain.Decision decideOffLock(HandLog snapshot, String botId, String tableId, String street) {
        Future<BotBrain.Decision> future;
        try {
            future = decidePool.submit(() -> brain.decide(snapshot.finalState(), botId));
        } catch (RejectedExecutionException ex) {
            log.error("봇 판단 스레드 고갈 — 안전 액션으로 대체(table={}, bot={}, street={})",
                    tableId, botId, street);
            return null;
        }
        try {
            return future.get(decideTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.error("봇 판단 시간 초과({}ms) — 안전 액션으로 대체(table={}, bot={}, street={})",
                    decideTimeoutMillis, tableId, botId, street);
            return null;
        } catch (ExecutionException ex) {
            log.error("봇 판단 실패 — 안전 액션으로 대체(table={}, bot={}, street={})",
                    tableId, botId, street, ex.getCause());
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return null;
        }
    }

    /**
     * 봇 액션 기록(시간순). 진행 중 핸드의 것은 봇 핸드 강도가 새므로 기본 제외 —
     * includeCurrentHand 는 전지적 관찰(godview)과 같은 신뢰 수준에서만 켠다.
     */
    public List<BotAction> reasons(String tableId, boolean includeCurrentHand) {
        Table table = tableService.getOrCreate(tableId);
        synchronized (table) {
            boolean hideCurrent = table.handInProgress() && !includeCurrentHand;
            int currentHand = table.handsPlayed();
            return reasonLog.getOrDefault(tableId, new ArrayDeque<>()).stream()
                    .filter(a -> !hideCurrent || a.handNo() != currentHand)
                    .toList();
        }
    }

    /** 두뇌·액션 실패 시의 최후 수단: 체크 가능하면 체크, 아니면 폴드(항상 합법). */
    private static BotBrain.Decision safeDecision(Table table, String botId) {
        boolean canCheck = table.engine().legalActions(botId).contains(ActionType.CHECK);
        return new BotBrain.Decision(canCheck ? "CHECK" : "FOLD", 0,
                "판단 오류 → 안전 " + (canCheck ? "체크" : "폴드"));
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
