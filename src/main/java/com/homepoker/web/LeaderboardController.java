package com.homepoker.web;

import com.homepoker.stats.StatsService;
import com.homepoker.web.dto.LeaderboardRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ROI 리더보드 REST 엔드포인트. 프론트가 GET /api/leaderboard 로 폴링한다. */
@RestController
public class LeaderboardController {

    private final StatsService statsService;

    public LeaderboardController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/api/leaderboard")
    public List<LeaderboardRow> leaderboard() {
        return statsService.leaderboard();
    }
}
