package com.homepoker.table;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.HandLog;
import com.homepoker.engine.game.Player;
import com.homepoker.web.dto.HandSummaryView;
import com.homepoker.web.dto.PotView;
import com.homepoker.web.dto.ReplayFrame;
import com.homepoker.web.dto.SeatView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 완료된 핸드의 이벤트 소싱 기록(HandLog)을 프론트가 되감기 좋은 프레임 목록으로 변환한다.
 * 지난 핸드이므로 리댁션 없이 모든 홀카드를 공개한다(리뷰용).
 */
@Service
public class ReplayService {

    private final TableService tableService;

    public ReplayService(TableService tableService) {
        this.tableService = tableService;
    }

    /** 테이블의 완료 핸드 목록(최신이 index 0). */
    public List<HandSummaryView> summaries(String tableId) {
        List<HandLog> history = tableService.getOrCreate(tableId).history();
        List<HandSummaryView> out = new ArrayList<>(history.size());
        for (int i = 0; i < history.size(); i++) {
            HandLog log = history.get(i);
            HandEngine end = log.finalState();
            out.add(new HandSummaryView(
                    i,
                    log.seats().stream().map(HandLog.Seat::name).toList(),
                    log.actionCount(),
                    end.board().stream().map(Card::toString).toList(),
                    end.payouts(),
                    end.wentToShowdown()));
        }
        return out;
    }

    /** 한 핸드를 프레임 0..N(N=액션 수)으로 펼친다. 각 프레임은 그 시점의 전체 상태. */
    public List<ReplayFrame> frames(String tableId, int handIndex) {
        List<HandLog> history = tableService.getOrCreate(tableId).history();
        if (handIndex < 0 || handIndex >= history.size()) {
            throw new IllegalArgumentException("없는 핸드 인덱스: " + handIndex);
        }
        HandLog log = history.get(handIndex);
        List<ReplayFrame> frames = new ArrayList<>(log.actionCount() + 1);
        for (int step = 0; step <= log.actionCount(); step++) {
            HandEngine e = log.stateAt(step);
            String actionDesc = step == 0 ? null : describe(log.actions().get(step - 1));
            frames.add(toFrame(step, actionDesc, e));
        }
        return frames;
    }

    private ReplayFrame toFrame(int step, String actionDesc, HandEngine e) {
        String actingId = e.playerToAct() == null ? null : e.playerToAct().id();
        List<SeatView> seats = e.players().stream()
                .map(p -> toSeatView(e, p, actingId))
                .toList();
        List<PotView> pots = e.pots().stream()
                .map(pot -> new PotView(pot.amount(), pot.eligiblePlayerIds()))
                .toList();
        return new ReplayFrame(
                step, actionDesc, e.street().name(),
                e.board().stream().map(Card::toString).toList(),
                e.pot(), pots, seats, actingId, e.payouts());
    }

    /** 리플레이는 지난 핸드라 모든 홀카드를 공개한다(폴드 포함). */
    private SeatView toSeatView(HandEngine e, Player p, String actingId) {
        boolean isButton = e.players().indexOf(p) == e.buttonSeat();
        List<String> hole = p.holeCards().stream().map(Card::toString).toList();
        return new SeatView(
                p.id(), p.name(), p.stack(), p.status().name(),
                e.committedThisStreet(p.id()), hole,
                isButton, p.id().equals(actingId), null,
                HandLabels.of(p.holeCards(), e.board()));
    }

    private String describe(Action a) {
        if (a.type() == ActionType.BET || a.type() == ActionType.RAISE) {
            return a.playerId() + " " + a.type() + " " + a.amount();
        }
        return a.playerId() + " " + a.type();
    }
}
