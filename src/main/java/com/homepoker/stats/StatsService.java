package com.homepoker.stats;

import com.homepoker.web.dto.LeaderboardRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 누적 통계 집계 + ROI 리더보드. 핸드가 끝날 때마다 HandReport 를 받아 갱신한다.
 * 게임 규칙을 모르는 순수 집계 계층.
 *
 * 영속화는 StatsStore 포트에 위임한다 — 기동 시 복원, 매 핸드 후 스냅샷 저장.
 * 저장 방식(파일/DB)은 이 서비스가 모른다.
 */
@Service
public class StatsService {

    private final Map<String, PlayerStats> stats = new ConcurrentHashMap<>();
    private final StatsStore store;

    @Autowired
    public StatsService(StatsStore store) {
        this.store = store;
        for (PlayerStatsSnapshot snap : store.load()) {
            stats.put(snap.playerId(), PlayerStats.fromSnapshot(snap));
        }
    }

    /** 영속화하지 않는 인메모리 서비스(단위테스트용). */
    public StatsService() {
        this(StatsStore.NOOP);
    }

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
        persist();
    }

    private void persist() {
        store.save(stats.values().stream().map(PlayerStats::toSnapshot).toList());
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
