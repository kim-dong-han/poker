package com.homepoker.stats;

import com.homepoker.web.dto.LeaderboardRow;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 누적 통계 집계 + ROI 리더보드. 핸드가 끝날 때마다 HandReport 를 받아 갱신한다.
 * 게임 규칙을 모르는 순수 집계 계층.
 */
@Service
public class StatsService {

    private final Map<String, PlayerStats> stats = new ConcurrentHashMap<>();

    public void record(HandReport report) {
        for (String id : report.dealt()) {
            PlayerStats ps = stats.computeIfAbsent(id, PlayerStats::new);
            ps.setName(report.names().get(id));
            ps.addHand(
                    report.voluntaryPreflop().contains(id),
                    report.preflopRaisers().contains(id),
                    report.winners().contains(id),
                    report.netDelta().getOrDefault(id, 0L));
        }
    }

    public PlayerStats statsFor(String playerId) {
        return stats.get(playerId);
    }

    /** 순손익(net) 내림차순 리더보드. */
    public List<LeaderboardRow> leaderboard() {
        return stats.values().stream()
                .sorted(Comparator.comparingLong(PlayerStats::netProfit).reversed())
                .map(p -> new LeaderboardRow(
                        p.playerId(),
                        p.name(),
                        p.handsPlayed(),
                        p.handsWon(),
                        p.netProfit(),
                        (int) Math.round(p.vpip() * 100),
                        (int) Math.round(p.pfr() * 100)))
                .toList();
    }
}
