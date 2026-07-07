# ARCHITECTURE — 구조 설계

## 큰 그림

```
[React SPA] --STOMP/SockJS--> [Spring Boot]
 테이블 UI / 액션 버튼          ├─ TableController   (STOMP 진입점, 액션 주체 = principal)
 개인화 상태 구독               ├─ TableService      (테이블별 개인화·리댁션 뷰)
 이퀴티 오버레이               ├─ Table             (테이블당 직렬화 = 단일 라이터)
                              ├─ RuleGuard         (바이인·재입장·리로드 정책)
                              ├─ EquityService     (몬테카를로, 본인 이퀴티만 push)
                              ├─ StatsService      (VPIP/PFR/net 집계)
                              └─ HandEngine ★      (순수 POJO 상태머신, DB/WS 무관)
                                   ├─ card  (Card / Rank / Suit / Deck)
                                   ├─ eval  (HandEvaluator: 7장 중 best5)
                                   └─ game  (베팅 라운드 · 사이드팟 · 쇼다운)
```

★ = 프로젝트의 코어. 나머지는 전부 이 위에 얇게 얹힌 계층이다.

---

## 설계 철칙 (변경 금지)

1. **`HandEngine`은 WebSocket/DB/React를 전혀 모른다.**
   순수 도메인이라 단위테스트로 100% 검증 가능. Spring 타입을 import하는 순간 이 철칙이 깨진다.
   → 300판 무작위 자동플레이로 "칩 총량 보존·정상 종료" 속성을 검증한다.

2. **홀카드 리댁션(redaction).**
   서버는 각 플레이어에게 `/user/queue/table.{id}`로 *본인 관점의* 상태만 보낸다.
   상대 홀카드는 쇼다운 전까지 전송선(wire)에 실리지 않는다. 통합테스트로 강제.

3. **테이블당 단일 라이터.**
   `Table`이 상태 변경을 직렬화해, 순수 엔진이 동시 접근을 절대 받지 않게 한다.
   락을 엔진 안에 넣지 않는다 — 동시성 경계는 `Table`이 전담한다.

4. **액션 주체는 세션 principal로 못박는다.**
   `ActionRequest`에 playerId를 신뢰하지 않는다. `principal.getName()`만 신뢰 → 대리 액션 원천 차단.

---

## 패키지 맵

```
com.homepoker
├─ engine                      ★ 순수 도메인 (Spring 무관)
│  ├─ card                     Card, Rank, Suit, Deck
│  ├─ eval                     HandCategory, HandRank, HandEvaluator
│  ├─ game                     HandEngine, Player, Action, Street,
│  │                           Pot, SidePots, ActionType, PlayerStatus
│  └─ demo                     ConsoleHandDemo (WebSocket 없이 한 판)
├─ table                       Table, TableService     (동시성 경계·개인화)
├─ rule                        RuleGuard, BuyInPolicy, RuleViolation, RuleGuardConfig
├─ equity                      Equity, EquityService   (몬테카를로 오버레이)
├─ stats                       PlayerStats, HandReport, StatsService
└─ web                         TableController, LeaderboardController,
   ├─ dto                      WebSocketConfig, StompPrincipal
   └─ ...                      JoinRequest, ActionRequest, *View DTO
```

## 계층 의존 방향

```
web  →  table / rule / equity / stats  →  engine
                                          ↑ (engine은 아무것도 위로 의존하지 않음)
```

- `engine`은 최하단. 위 계층을 절대 import하지 않는다.
- `web`은 도메인 객체를 그대로 노출하지 않고 `dto/*View`로 변환해 내보낸다(리댁션 지점).

## STOMP 엔드포인트

| 대상 | 목적지 | 방향 |
|------|--------|------|
| 착석 | `/app/table/{id}/join` | 클라 → 서버 |
| 핸드 시작 | `/app/table/{id}/start` | 클라 → 서버 |
| 액션 | `/app/table/{id}/action` | 클라 → 서버 |
| 개인화 상태 | `/user/queue/table.{id}` | 서버 → 클라 (사람마다 다른 리댁션 뷰) |
| 에러 사유 | `/user/queue/errors` | 서버 → 요청자 |

## 게임 엔진 상태머신

```
PREFLOP → FLOP → TURN → RIVER → SHOWDOWN → COMPLETE
   └─ 각 스트리트 안에서 베팅 라운드 (needsToAct[] 소진 시 다음 스트리트)
   └─ 폴드 무혈입성 시 SHOWDOWN 건너뛰고 바로 COMPLETE
   └─ 전원 올인 시 보드 끝까지 깔고 정산
```

- 액션을 `apply(Action)`로 하나씩 먹이면 스트리트 전환·사이드팟·쇼다운까지 스스로 진행.
- 알려진 단순화(포트폴리오 범위): 숏 올인(풀 레이즈 미만)은 이미 액션한 플레이어에게
  재레이즈 권리를 다시 열지 않는다(TDA 규칙과 동일). 러닝잇트와이스 등 변형 미지원.

## HTTP 엔드포인트

| 메서드 | 경로 | 응답 |
|--------|------|------|
| GET | `/api/leaderboard` | ROI 리더보드(VPIP/PFR/net) |
| GET | `/api/tables` | 로비: 활성 테이블 목록(좌석수·진행상태) |
| GET | `/api/tables/{id}/hands` | 완료 핸드 목록(최신순, 이벤트 소싱 요약) |
| GET | `/api/tables/{id}/hands/{index}` | 그 핸드의 프레임 단위 리플레이(전 카드 공개) |

## 데이터 & 상태

- **주로 인메모리**. 영속 DB 없음(포트폴리오 범위). 테이블·핸드 상태는 프로세스 수명 동안만 유지.
- **통계는 파일 영속화**: `StatsService`가 `StatsStore` 포트로 위임 →
  `JsonFileStatsStore`가 매 핸드 후 JSON 스냅샷 저장(원자적 temp→move), 기동 시 복원.
  DB 대신 파일을 택한 이유: 보존 대상이 플레이어별 소수 카운터뿐이고 Lightsail 2GB 저사양 배려.
- **이벤트 소싱**: `HandEngine`이 초기 조건(좌석·버튼·블라인드·덱 순서)+적용 액션을 `HandLog`로 기록.
  `HandLog.stateAt(k)`가 순수 엔진을 다시 돌려 임의 시점 상태를 결정적으로 복원 → 핸드 리플레이.
  `Table`이 완료 핸드를 최근 50개까지 보관.

## 확장 시 지켜야 할 것

- 새 규칙 → `rule` 패키지에 정책으로 추가. 엔진에 if문으로 박지 않는다.
- 새 실시간 기능 → `web`에서 처리하고 도메인은 `View` DTO로만 노출.
- 무거운 연산 → `engine`을 오염시키지 말고 별도 서비스(예: `EquityService`)로 격리.
- 시간/타이머 → 엔진은 시간을 모른다. 타임뱅크(`TurnTimer`+`TurnTimeoutSweeper`)는 `table` 계층에
  두고 `Clock` 주입으로 테스트한다. 자동 액션도 일반 액션과 같은 경로(`enforceTimeout`→`applyAction`)로 넣어
  통계·버스트 처리가 동일하게 돌게 한다.
