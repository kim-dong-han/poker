# 홈포커 (Home Poker)

> 제대로 된 룰을 강제하는 실시간 한국어 홈게임 포커 서버.
> "배포"가 아니라 **"이해하고 만들었는가"**에 초점을 둔 포트폴리오 프로젝트.

노리밋 텍사스 홀덤을 **순수 도메인 게임 엔진**으로 구현하고, 그 위에 STOMP/WebSocket
실시간 계층과 React 테이블 UI를 얇게 올린 구조입니다. 핵심 차별점은 한국 웹보드의
'박아박아'를 코드로 막는 **RuleGuard** 정책(진행 중)입니다.

## 아키텍처

```
[React SPA] --STOMP/SockJS--> [Spring Boot]
 테이블 UI / 액션 버튼          ├─ TableController  (STOMP 진입점, 액션 주체 = principal)
 개인화 상태 구독               ├─ TableService     (테이블별 개인화·리댁션 뷰)
                               ├─ Table            (테이블당 직렬화 = 단일 라이터)
                               └─ HandEngine ★     (순수 POJO 상태머신, DB/WS 무관)
                                    ├─ card  (Card/Rank/Suit/Deck)
                                    ├─ eval  (HandEvaluator: 7장 중 best5)
                                    └─ game  (베팅 라운드·사이드팟·쇼다운)
```

### 설계 철칙
- **`HandEngine`은 WebSocket/DB/React를 전혀 모른다.** 순수 도메인이라 단위테스트로 100% 검증
  가능 — 동시성/상태관리 역량의 증거. (300판 무작위 자동플레이로 칩 보존·종료 속성 테스트)
- **홀카드 리댁션**: 서버는 각 플레이어에게 `/user/queue/**`로 *본인 관점의* 상태만 보낸다.
  상대 홀카드는 쇼다운 전까지 전송선에 실리지 않는다 (통합테스트로 검증).
- **테이블당 단일 라이터**: `Table`의 상태 변경을 직렬화해 순수 엔진이 동시 접근을 받지 않게 한다.

## 실행

사전 요구: JDK 21, Node 20+.

```bash
# 1) 프론트엔드 빌드 → Spring 정적 리소스로 출력
cd frontend
npm install
npm run build          # 산출물: ../src/main/resources/static

# 2) 백엔드 실행
cd ..
./gradlew bootRun      # http://localhost:8080

# 콘솔로 한 판만 돌려보기(WebSocket 없이 순수 엔진)
./gradlew runDemo --args="42"
```

브라우저 탭 두 개를 열어 서로 다른 ID(예: `alice`, `bob`)로 착석하면 2인 홀덤을 진행할 수 있습니다.
로컬 프론트 개발은 `cd frontend && npm run dev`(Vite :5173, `/ws`는 :8080으로 프록시).

## 테스트

```bash
./gradlew test
```

- `eval`: 족보 분류·휠·키커·7장 best5
- `game`: 헤즈업 쇼다운, 폴드 무혈입성, 3인 사이드팟, 규칙 위반 거부, **300판 칩보존 속성 테스트**
- `table`: 로비/리댁션/차례 검증
- `web`: 실제 WebSocket 위에서 2인 착석·핸드시작·홀카드 리댁션 end-to-end

## 로드맵 (빌드 순서)
1. ✅ 게임 엔진 도메인 + 단위테스트
2. ✅ STOMP 연동 + React 테이블 UI (2인용)
3. ✅ **RuleGuard**: 최소 바이인 강제 / 버스트 후 재입장 쿨다운 / 일일 리로드 한도
4. ✅ 이퀴티 오버레이 (몬테카를로, 본인 이퀴티만 push)
5. ✅ StatsService + ROI 리더보드 (VPIP/PFR/net, `GET /api/leaderboard`)
6. ✅ **핸드 히스토리 리플레이** (이벤트 소싱 `HandLog`, 프레임 단위 되감기, `GET /api/tables/{id}/hands`)
7. ✅ **멀티 테이블 로비** (`GET /api/tables`) + **타임뱅크** (30초 제한시간, 초과 시 자동 체크/폴드)
8. ✅ **통계 영속화** (JSON 파일 스냅샷, 재시작 후 리더보드 복원)
9. ✅ **관전 모드**(`/spectate`) + **프리플랍 레인지 오버레이**(6-max RFI, `/api/preflop-range`)

전체 74개 단위·통합 테스트 통과. 로드맵 완료 — 다음 후보는 [TODO.md](TODO.md) 백로그 참고.

## 배포

프론트+백을 한 jar로 묶어 배포한다. `docker build -t homepoker . && docker run -p 8080:8080 homepoker`
또는 bare jar + systemd. 저사양(Lightsail 2GB) 튜닝과 단계별 절차는 [DEPLOY.md](DEPLOY.md) 참고.
