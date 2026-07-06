package com.homepoker.table;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.Player;
import com.homepoker.engine.game.PlayerStatus;
import com.homepoker.rule.RuleGuard;
import com.homepoker.web.dto.PotView;
import com.homepoker.web.dto.SeatView;
import com.homepoker.web.dto.TableStateView;
import org.springframework.stereotype.Service;

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

    private final Map<String, Table> tables = new ConcurrentHashMap<>();
    private final RuleGuard ruleGuard;

    public TableService(RuleGuard ruleGuard) {
        this.ruleGuard = ruleGuard;
    }

    public Table getOrCreate(String tableId) {
        return tables.computeIfAbsent(tableId, id -> new Table(id, DEFAULT_SB, DEFAULT_BB));
    }

    /** 착석. RuleGuard 가 최소/최대 바이인과 버스트 재입장 쿨다운을 먼저 강제한다. */
    public void join(String tableId, String playerId, String name, long buyIn) {
        ruleGuard.checkJoin(playerId, buyIn);
        getOrCreate(tableId).seat(playerId, name, buyIn);
    }

    public void startHand(String tableId) {
        getOrCreate(tableId).startHand();
    }

    public void applyAction(String tableId, String playerId, String type, long amount) {
        Table table = getOrCreate(tableId);
        table.applyAction(playerId, type, amount);
        settleBustsIfHandComplete(table);
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
     * viewerId 관점의 테이블 상태. 상대 홀카드는 담기지 않는다(쇼다운 공개 대상만 예외).
     */
    public TableStateView viewFor(String tableId, String viewerId) {
        Table table = getOrCreate(tableId);
        HandEngine engine = table.engine();

        if (engine == null) {
            // 아직 핸드 전: 로비 상태
            List<SeatView> seats = table.seatedPlayers().stream()
                    .map(p -> new SeatView(p.id(), p.name(), p.stack(), "WAITING",
                            0, null, false, false))
                    .toList();
            return new TableStateView(tableId, false, "WAITING", List.of(), 0,
                    List.of(), seats, null, Set.of(), 0, 0, Map.of());
        }

        boolean revealAll = engine.isComplete() && engine.wentToShowdown();
        String currentActorId = engine.playerToAct() == null ? null : engine.playerToAct().id();

        List<SeatView> seats = engine.players().stream()
                .map(p -> toSeatView(engine, p, viewerId, revealAll, currentActorId))
                .toList();

        List<PotView> pots = engine.pots().stream()
                .map(pot -> new PotView(pot.amount(), pot.eligiblePlayerIds()))
                .toList();

        Set<String> legal = engine.legalActions(viewerId).stream()
                .map(ActionType::name)
                .collect(Collectors.toSet());

        long toCall = isSeated(engine, viewerId) ? engine.amountToCall(viewerId) : 0;

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
                engine.payouts());
    }

    private SeatView toSeatView(HandEngine engine, Player p, String viewerId,
                                boolean revealAll, String currentActorId) {
        boolean isViewer = p.id().equals(viewerId);
        boolean reveal = isViewer || (revealAll && p.status() != PlayerStatus.FOLDED);
        List<String> hole = reveal ? p.holeCards().stream().map(Card::toString).toList() : null;
        boolean isButton = engine.players().indexOf(p) == engine.buttonSeat();
        return new SeatView(
                p.id(), p.name(), p.stack(), p.status().name(),
                engine.committedThisStreet(p.id()), hole,
                isButton, p.id().equals(currentActorId));
    }

    private boolean isSeated(HandEngine engine, String playerId) {
        return engine.players().stream().anyMatch(p -> p.id().equals(playerId));
    }
}
