package com.homepoker.table;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 핸드가 끝나면 잠깐(결과를 볼 시간) 기다렸다가 자동으로 다음 핸드를 시작한다.
 * 실제 온라인 포커처럼 "칩이 있는 한 게임은 계속" — 칩 보유 플레이어가 2명 미만이 되면
 * (버스트 등) 자연히 멈춘다. 첫 핸드는 여전히 사람이 시작한다(완료된 핸드가 있어야 발동).
 *
 * 판단·동시성은 여기서, 주기 호출·브로드캐스트는 스위퍼가 담당(BotService 와 동일 패턴).
 * Clock 주입으로 대기 시간을 결정적으로 테스트한다(TurnTimer 와 동일 패턴).
 */
@Service
public class AutoDealService {

    private final TableService tableService;
    private final Clock clock;
    private final Duration delay;
    private final long showdownExtraMillis;

    /** 테이블별 자동 진행 여부(기본 켬). */
    private final Map<String, Boolean> enabled = new ConcurrentHashMap<>();
    /** 테이블별 다음 핸드를 시작하기로 예약된 시각(epoch ms) — 결과 감상 지연 구현. */
    private final Map<String, Long> dealAt = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AutoDealService(TableService tableService, Clock clock,
                           @Value("${poker.autodeal.delay-ms:4000}") long delayMillis,
                           @Value("${poker.autodeal.showdown-extra-ms:3500}") long showdownExtraMillis) {
        this.tableService = tableService;
        this.clock = clock;
        this.delay = Duration.ofMillis(delayMillis);
        this.showdownExtraMillis = showdownExtraMillis;
    }

    /** 쇼다운 추가 대기 기본값(3.5초)을 쓰는 편의 생성자(기존 테스트 호환). */
    public AutoDealService(TableService tableService, Clock clock, long delayMillis) {
        this(tableService, clock, delayMillis, 3500);
    }

    public boolean isEnabled(String tableId) {
        return enabled.getOrDefault(tableId, true);
    }

    public void setEnabled(String tableId, boolean on) {
        enabled.put(tableId, on);
        if (!on) {
            dealAt.remove(tableId); // 예약된 자동 시작도 함께 취소
        }
    }

    /**
     * 완료된 핸드가 대기 시간을 넘겼으면 다음 핸드를 시작한다. 스위퍼가 주기적으로 호출한다.
     * Table 모니터로 감싸 사람의 수동 시작과의 경합에서 이중 시작을 막는다.
     *
     * @return 새 핸드를 실제로 시작했으면 true(호출측이 브로드캐스트)
     */
    public boolean dealIfDue(String tableId) {
        Table table = tableService.getOrCreate(tableId);
        synchronized (table) {
            if (!isEnabled(tableId) || table.engine() == null || table.handInProgress()) {
                dealAt.remove(tableId);
                return false;
            }
            long now = clock.millis();
            Long due = dealAt.get(tableId);
            if (due == null) {
                // 종료를 처음 본 시점부터 카운트. 쇼다운(특히 올인 런아웃)은 카드 공개 연출과
                // 결과를 볼 시간이 더 필요하므로 추가 대기를 얹는다(폴드 종료는 기본 지연만).
                long extra = table.engine().wentToShowdown() ? showdownExtraMillis : 0;
                dealAt.put(tableId, now + delay.toMillis() + extra);
                return false;
            }
            if (now < due) {
                return false;
            }
            dealAt.remove(tableId);
            long withChips = table.seatedPlayers().stream().filter(p -> p.stack() > 0).count();
            if (withChips < 2) {
                return false; // 버스트 등으로 인원 부족 — 사람이 다시 채울 때까지 중단
            }
            tableService.startHand(tableId);
            return true;
        }
    }
}
