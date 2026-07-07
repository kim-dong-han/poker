package com.homepoker.stats;

import java.util.List;

/**
 * 통계 스냅샷의 영속 포트. StatsService 는 파일/DB 구현을 모른 채 이 인터페이스로만 저장·복원한다
 * (헥사고날 포트). 기본 운영 구현은 JsonFileStatsStore, 테스트는 NOOP 또는 인메모리.
 */
public interface StatsStore {

    List<PlayerStatsSnapshot> load();

    void save(List<PlayerStatsSnapshot> snapshots);

    /** 영속화하지 않는 구현(단위테스트·순수 인메모리 실행용). */
    StatsStore NOOP = new StatsStore() {
        @Override
        public List<PlayerStatsSnapshot> load() {
            return List.of();
        }

        @Override
        public void save(List<PlayerStatsSnapshot> snapshots) {
            // 저장하지 않는다
        }
    };
}
