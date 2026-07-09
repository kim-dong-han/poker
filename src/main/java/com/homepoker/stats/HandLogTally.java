package com.homepoker.stats;

import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.HandLog;
import com.homepoker.engine.game.Player;
import com.homepoker.engine.game.PlayerStatus;
import com.homepoker.engine.game.Street;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 종료된 핸드의 로그(이벤트 소싱)를 한 번 재생해 포스트플랍 통계 원료를 뽑는다.
 * 해링턴 상대 모델링 수치(AF/WtSD/F3B)의 분자·분모가 여기서 나온다.
 * 순수 집계 — 액션 핫패스가 아니라 핸드 종료 시 1회만 돈다.
 */
public final class HandLogTally {

    /** 한 핸드에서 뽑은 포스트플랍 지표 원료. */
    public record Tally(
            Set<String> sawFlop,
            Map<String, Integer> postflopAggr,
            Map<String, Integer> postflopCalls,
            Set<String> showdown,
            Set<String> facedThreeBet,
            Set<String> foldedToThreeBet) {}

    private HandLogTally() {
    }

    public static Tally tally(HandLog log) {
        HandEngine e = log.stateAt(0);
        Set<String> sawFlop = new HashSet<>();
        Map<String, Integer> aggr = new HashMap<>();
        Map<String, Integer> calls = new HashMap<>();
        Set<String> faced3B = new HashSet<>();
        Set<String> folded3B = new HashSet<>();

        String opener = null;      // 첫 프리플랍 레이저
        int preflopRaises = 0;
        boolean flopCaptured = false;

        for (Action a : log.actions()) {
            Street st = e.street();
            if (st == Street.PREFLOP) {
                if (a.type() == ActionType.RAISE) {
                    preflopRaises++;
                    if (opener == null) {
                        opener = a.playerId();
                    } else if (preflopRaises == 2) {
                        faced3B.add(opener); // 오픈 레이저가 3벳을 마주함
                    }
                }
                if (preflopRaises >= 2 && a.playerId().equals(opener)
                        && a.type() == ActionType.FOLD) {
                    folded3B.add(opener);
                }
            } else {
                if (a.type() == ActionType.BET || a.type() == ActionType.RAISE) {
                    aggr.merge(a.playerId(), 1, Integer::sum);
                } else if (a.type() == ActionType.CALL) {
                    calls.merge(a.playerId(), 1, Integer::sum);
                }
            }
            e.apply(a);
            if (!flopCaptured && e.board().size() >= 3) { // 올인 런아웃 포함, 플랍 공개 시점
                flopCaptured = true;
                for (Player p : e.players()) {
                    if (p.status() != PlayerStatus.FOLDED) {
                        sawFlop.add(p.id());
                    }
                }
            }
        }

        Set<String> showdown = new HashSet<>();
        if (e.wentToShowdown()) {
            for (Player p : e.players()) {
                if (p.status() != PlayerStatus.FOLDED) {
                    showdown.add(p.id());
                }
            }
        }
        return new Tally(sawFlop, aggr, calls, showdown, faced3B, folded3B);
    }
}
