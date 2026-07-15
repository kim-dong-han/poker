package com.homepoker.table;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.Player;
import com.homepoker.engine.game.PlayerStatus;
import com.homepoker.engine.game.Street;
import com.homepoker.equity.Equity;
import com.homepoker.equity.EquityService;
import com.homepoker.rule.RuleGuard;
import com.homepoker.rule.RuleViolation;
import com.homepoker.stats.HandReport;
import com.homepoker.stats.StatsService;
import com.homepoker.web.dto.LobbyRow;
import com.homepoker.web.dto.PotView;
import com.homepoker.web.dto.SeatView;
import com.homepoker.web.dto.TableStateView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 여러 테이블을 관리하고, 각 플레이어에게 맞춘(리댁션된) 상태 뷰를 만든다.
 * Spring 컴포넌트지만 게임 규칙은 전혀 모른다 — 순수 도메인(HandEngine)과 전송 계층 사이의 얇은 층.
 */
@Service
public class TableService {

    private static final long DEFAULT_SB = 10;
    private static final long DEFAULT_BB = 20;

    /**
     * 라이브 이퀴티 오버레이의 몬테카를로 반복 횟수. 저사양 배포(Lightsail 1 vCPU)에서는
     * poker.equity.live-iterations 로 낮춰 CPU 부하를 조절한다(기본 1500).
     */
    @Value("${poker.equity.live-iterations:1500}")
    private int liveEquityIterations = 1500;

    private final Map<String, Table> tables = new ConcurrentHashMap<>();
    private final Map<String, HandAccumulator> accumulators = new ConcurrentHashMap<>();
    private final RuleGuard ruleGuard;
    private final EquityService equityService;
    private final StatsService statsService;
    private final TurnTimer turnTimer;

    public TableService(RuleGuard ruleGuard, EquityService equityService,
                        StatsService statsService, TurnTimer turnTimer) {
        this.ruleGuard = ruleGuard;
        this.equityService = equityService;
        this.statsService = statsService;
        this.turnTimer = turnTimer;
    }

    public Table getOrCreate(String tableId) {
        return tables.computeIfAbsent(tableId, id -> new Table(id, DEFAULT_SB, DEFAULT_BB));
    }

    /** 로비: 현재 존재하는 모든 테이블의 공개 요약(테이블 id 오름차순). */
    public List<LobbyRow> lobby() {
        return tables.values().stream()
                .sorted(Comparator.comparing(Table::id))
                .map(t -> new LobbyRow(
                        t.id(), t.smallBlind(), t.bigBlind(),
                        t.seatedCount(), t.handInProgress(), t.handsPlayed()))
                .toList();
    }

    /** 착석. RuleGuard 가 최소/최대 바이인과 버스트 재입장 쿨다운을 먼저 강제한다. */
    public void join(String tableId, String playerId, String name, long buyIn) {
        ruleGuard.checkJoin(playerId, buyIn);
        getOrCreate(tableId).seat(playerId, name, buyIn);
    }

    /**
     * 리바인: 버스트로 자리를 잃은 플레이어가 쿨다운 없이 즉시 다시 산다.
     * 착석 중이면 거부(칩 추가는 리로드 경로). 횟수 한도는 RuleGuard(maxRebuys)가 관리.
     */
    public void rebuy(String tableId, String playerId, String name, long buyIn) {
        Table table = getOrCreate(tableId);
        if (table.isSeated(playerId)) {
            throw new RuleViolation("이미 착석 중입니다 — 리바인은 버스트 후에만 가능합니다.");
        }
        ruleGuard.checkAndRecordRebuy(playerId, buyIn);
        table.seat(playerId, name, buyIn);
    }

    public void startHand(String tableId) {
        Table table = getOrCreate(tableId);
        // 블라인드가 빠지기 전(핸드 시작 전) 스택을 캡처해야 순손익에 블라인드가 반영된다.
        Map<String, Long> preHandStacks = new HashMap<>();
        for (Player p : table.seatedPlayers()) {
            preHandStacks.put(p.id(), p.stack());
        }
        table.startHand();
        accumulators.put(tableId, HandAccumulator.forHand(table.engine(), preHandStacks));
        syncTurnTimer(tableId, table);
    }

    public void applyAction(String tableId, String playerId, String type, long amount) {
        Table table = getOrCreate(tableId);
        Street streetBefore = table.engine() == null ? null : table.engine().street();
        table.applyAction(playerId, type, amount);

        recordPreflopTendency(tableId, playerId, type, streetBefore);
        finalizeStatsIfHandComplete(tableId, table);
        settleBustsIfHandComplete(table);
        syncTurnTimer(tableId, table);
    }

    /** 상태 변화 후: 액션 대기자가 있으면 제한시간을 새로 걸고, 없으면(핸드 종료 등) 해제한다. */
    private void syncTurnTimer(String tableId, Table table) {
        if (table.handInProgress() && table.engine().playerToAct() != null) {
            turnTimer.startTurn(tableId);
        } else {
            turnTimer.clear(tableId);
        }
    }

    /**
     * 현재 액션자의 제한시간이 지났으면 대신 자동 액션을 넣는다(체크 가능하면 체크, 아니면 폴드).
     * 스케줄러가 주기적으로 호출한다. Table 모니터로 감싸 유저 액션과의 경합에서 원자성을 보장한다.
     *
     * @return 자동 액션을 실제로 넣었으면 true
     */
    public boolean enforceTimeout(String tableId) {
        Table table = getOrCreate(tableId);
        synchronized (table) {
            if (!table.handInProgress()) {
                turnTimer.clear(tableId);
                return false;
            }
            HandEngine engine = table.engine();
            Player actor = engine.playerToAct();
            if (actor == null || !turnTimer.isExpired(tableId)) {
                return false;
            }
            String type = engine.legalActions(actor.id()).contains(ActionType.CHECK) ? "CHECK" : "FOLD";
            applyAction(tableId, actor.id(), type, 0); // 통계·버스트·타이머 재동기화까지 동일 경로로
            return true;
        }
    }

    /** 스케줄러가 훑을 현재 존재하는 테이블 id 목록. */
    public List<String> activeTableIds() {
        return List.copyOf(tables.keySet());
    }

    /** 프리플랍 액션이면 VPIP/PFR 성향을 이번 핸드 누적기에 기록. */
    private void recordPreflopTendency(String tableId, String playerId, String type, Street streetBefore) {
        HandAccumulator acc = accumulators.get(tableId);
        if (acc == null || streetBefore != Street.PREFLOP) {
            return;
        }
        ActionType at = ActionType.valueOf(type.toUpperCase());
        if (at == ActionType.CALL || at == ActionType.BET || at == ActionType.RAISE) {
            acc.voluntaryPreflop.add(playerId); // 블라인드 체크/폴드는 자발적 투자가 아님
        }
        if (at == ActionType.RAISE) {
            acc.preflopRaisers.add(playerId);
        }
    }

    /** 핸드 종료 시 순손익·승자를 계산해 StatsService 에 리포트. */
    private void finalizeStatsIfHandComplete(String tableId, Table table) {
        HandEngine engine = table.engine();
        HandAccumulator acc = accumulators.get(tableId);
        if (engine == null || !engine.isComplete() || acc == null) {
            return;
        }
        Map<String, Long> netDelta = new HashMap<>();
        Set<String> winners = new HashSet<>();
        for (Player p : engine.players()) {
            long start = acc.startStacks.getOrDefault(p.id(), p.stack());
            netDelta.put(p.id(), p.stack() - start);
        }
        engine.payouts().forEach((id, amt) -> {
            if (amt > 0) {
                winners.add(id);
            }
        });
        // 포스트플랍 지표(AF/WtSD/F3B 원료)는 종료된 핸드 로그를 1회 재생해 뽑는다
        var tally = com.homepoker.stats.HandLogTally.tally(engine.log());
        statsService.record(new HandReport(
                acc.names, acc.dealt, acc.voluntaryPreflop, acc.preflopRaisers, netDelta, winners,
                tally.sawFlop(), tally.postflopAggr(), tally.postflopCalls(),
                tally.showdown(), tally.facedThreeBet(), tally.foldedToThreeBet()));
        accumulators.remove(tableId);
    }

    /** 핸드가 끝났고 스택이 0인 플레이어는 버스트로 기록하고 자리를 비운다(재입장 쿨다운 시작). */
    private void settleBustsIfHandComplete(Table table) {
        var engine = table.engine();
        if (engine == null || !engine.isComplete()) {
            return;
        }
        for (var p : engine.players()) {
            if (p.stack() == 0 && table.isSeated(p.id())) {
                ruleGuard.recordBust(p.id());
                table.removeSeat(p.id());
            }
        }
    }

    public List<String> seatedPlayerIds(String tableId) {
        return getOrCreate(tableId).seatedPlayerIds();
    }

    /**
     * 브로드캐스트 수신 대상: 착석자 ∪ 현재 핸드 참가자.
     * 올인에서 져서 버스트한 플레이어는 종료 처리(settleBusts)로 좌석이 즉시 비워지는데,
     * 착석자에게만 전송하면 정작 패자는 마지막 종료 화면을 못 받아 클라이언트가
     * "AI 생각 중" 프레임에 영구 동결된 것처럼 보인다(스크린샷 55·56의 실제 원인).
     */
    public List<String> broadcastTargetIds(String tableId) {
        Table table = getOrCreate(tableId);
        var targets = new java.util.LinkedHashSet<>(table.seatedPlayerIds());
        HandEngine engine = table.engine();
        if (engine != null) {
            engine.players().forEach(p -> targets.add(p.id()));
        }
        return List.copyOf(targets);
    }

    /** 착석하지 않은 관전자용 뷰: 어떤 좌석의 홀카드도 보이지 않는다(쇼다운 공개분 제외). */
    private static final String SPECTATOR = "__spectator__";

    public TableStateView spectate(String tableId) {
        return viewFor(tableId, SPECTATOR, false);
    }

    /**
     * 전지적 관찰자 뷰: 폴드 포함 모든 좌석의 홀카드를 공개한다.
     * 로컬 홈게임의 관찰·학습용(예: 내가 폴드한 뒤 남은 판을 훈수 시점으로 보기) —
     * 착석자 개인 뷰(viewFor)와 달리 리댁션을 걸지 않으므로 실서비스에선 노출 범위를 제한할 것.
     */
    public TableStateView godView(String tableId) {
        return viewFor(tableId, SPECTATOR, true);
    }

    /**
     * viewerId 관점의 테이블 상태. 상대 홀카드는 담기지 않는다(쇼다운 공개 대상만 예외).
     */
    public TableStateView viewFor(String tableId, String viewerId) {
        return viewFor(tableId, viewerId, false);
    }

    private TableStateView viewFor(String tableId, String viewerId, boolean godEye) {
        Table table = getOrCreate(tableId);
        HandEngine engine = table.engine();

        if (engine == null) {
            // 아직 핸드 전: 로비 상태
            List<SeatView> seats = table.seatedPlayers().stream()
                    .map(p -> new SeatView(p.id(), p.name(), p.stack(), "WAITING",
                            0, null, false, false, null, null))
                    .toList();
            return new TableStateView(tableId, false, "WAITING", List.of(), 0,
                    List.of(), seats, null, Set.of(), 0, 0, Map.of(), Map.of(), null, 0);
        }

        boolean revealAll = engine.isComplete() && engine.wentToShowdown();
        String currentActorId = engine.playerToAct() == null ? null : engine.playerToAct().id();
        Map<String, String> lastActions = lastActionsThisStreet(engine);

        List<SeatView> seats = engine.players().stream()
                .map(p -> toSeatView(engine, p, viewerId, revealAll, currentActorId, godEye,
                        lastActions.get(p.id())))
                .toList();

        List<PotView> pots = engine.pots().stream()
                .map(pot -> new PotView(pot.amount(), pot.eligiblePlayerIds()))
                .toList();

        Set<String> legal = engine.legalActions(viewerId).stream()
                .map(ActionType::name)
                .collect(Collectors.toSet());

        long toCall = isSeated(engine, viewerId) ? engine.amountToCall(viewerId) : 0;
        Double viewerEquity = computeViewerEquity(engine, viewerId);

        return new TableStateView(
                tableId,
                !engine.isComplete(),
                engine.street().name(),
                engine.board().stream().map(Card::toString).toList(),
                engine.pot(),
                pots,
                seats,
                currentActorId,
                legal,
                toCall,
                engine.minRaiseTo(),
                engine.payouts(),
                engine.netResults(),
                viewerEquity,
                turnTimer.secondsLeft(tableId));
    }

    /**
     * 진행 중인 핸드에서 폴드하지 않은 관찰자에게 "본인 이퀴티만" 계산한다.
     * 상대 홀카드는 미지로 두고 몬테카를로로 추정하므로 상대 정보가 유출되지 않는다.
     */
    private Double computeViewerEquity(HandEngine engine, String viewerId) {
        if (engine.isComplete()) {
            return null;
        }
        Player hero = engine.players().stream()
                .filter(p -> p.id().equals(viewerId))
                .findFirst().orElse(null);
        if (hero == null || hero.status() == PlayerStatus.FOLDED || hero.holeCards().size() != 2) {
            return null;
        }
        int opponents = (int) engine.players().stream()
                .filter(p -> !p.id().equals(viewerId) && p.status() != PlayerStatus.FOLDED)
                .count();
        if (opponents < 1) {
            return null;
        }
        Equity equity = equityService.estimate(hero.holeCards(), engine.board(),
                opponents, liveEquityIterations, new java.util.Random());
        return equity.equity();
    }

    private SeatView toSeatView(HandEngine engine, Player p, String viewerId,
                                boolean revealAll, String currentActorId, boolean godEye,
                                String lastAction) {
        boolean isViewer = p.id().equals(viewerId);
        boolean reveal = godEye || isViewer || (revealAll && p.status() != PlayerStatus.FOLDED);
        List<String> hole = reveal ? p.holeCards().stream().map(Card::toString).toList() : null;
        // 족보 라벨은 홀카드가 공개된 좌석에만 계산 — 리댁션과 동일한 기준이라 정보 유출 없음
        String handLabel = reveal ? HandLabels.of(p.holeCards(), engine.board()) : null;
        boolean isButton = engine.players().indexOf(p) == engine.buttonSeat();
        return new SeatView(
                p.id(), p.name(), p.stack(), p.status().name(),
                engine.committedThisStreet(p.id()), hole,
                isButton, p.id().equals(currentActorId), lastAction, handLabel);
    }

    /**
     * 이번 스트리트에 각 플레이어가 한 마지막 액션(좌석 말풍선용). 이벤트 소싱 로그를 재생해
     * 스트리트가 바뀔 때마다 비운다 — 새 보드 카드가 깔리면 말풍선이 사라지는 효과.
     * 액션 금액은 BET/RAISE 만 의미 있으므로 "BET 60" 형태로 붙인다.
     */
    private static Map<String, String> lastActionsThisStreet(HandEngine engine) {
        var log = engine.log();
        HandEngine replay = log.stateAt(0);
        Map<String, String> out = new HashMap<>();
        var prev = replay.street();
        for (var a : log.actions()) {
            if (replay.street() != prev) {
                out.clear();
                prev = replay.street();
            }
            boolean sized = a.type() == ActionType.BET || a.type() == ActionType.RAISE;
            out.put(a.playerId(), sized ? a.type().name() + " " + a.amount() : a.type().name());
            replay.apply(a);
        }
        if (replay.street() != prev) { // 마지막 액션으로 스트리트가 넘어간 직후면 비운 상태로
            out.clear();
        }
        return out;
    }

    private boolean isSeated(HandEngine engine, String playerId) {
        return engine.players().stream().anyMatch(p -> p.id().equals(playerId));
    }

    /** 한 핸드 동안 통계 집계에 필요한 정보를 모으는 누적기(핸드 종료 시 HandReport 로 변환). */
    private static final class HandAccumulator {
        final Map<String, String> names = new HashMap<>();
        final Map<String, Long> startStacks = new HashMap<>();
        final Set<String> dealt = new HashSet<>();
        final Set<String> voluntaryPreflop = new HashSet<>();
        final Set<String> preflopRaisers = new HashSet<>();

        static HandAccumulator forHand(HandEngine engine, Map<String, Long> preHandStacks) {
            HandAccumulator acc = new HandAccumulator();
            for (Player p : engine.players()) {
                acc.names.put(p.id(), p.name());
                acc.startStacks.put(p.id(), preHandStacks.getOrDefault(p.id(), p.stack()));
                acc.dealt.add(p.id());
            }
            return acc;
        }
    }
}
