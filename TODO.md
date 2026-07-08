# TODO — 작업 목록

작업 관리 보드. 상태는 `[ ]` 대기 · `[~]` 진행중 · `[x]` 완료.
완료된 로드맵은 아래 "완료" 섹션에 보관하고, 위쪽에서 현재/다음 작업을 관리한다.

---

## 🔥 지금 (In Progress)

- [ ] <현재 잡고 있는 작업 — 하나만 두는 걸 권장>

## ⏭️ 다음 (Next — 우선순위 순)

- [ ] **bts-preflop.json 이전** — 새 작업 PC에는 차트 JSON이 없음(저작권상 gitignore라
      레포에 미포함). 이전 PC의 `src/main/resources/preflop/bts-preflop.json`을 복사해올 것.
      그전까지 봇 차트 기능은 자동 비활성(이퀴티 폴백)으로 동작
- [ ] **해링턴 캐시게임 전략 봇 적용** — 캐시게임 번역본(개인 소장 PDF) 전권 정독·전략 추출
      완료(요약: 로컬 `C:\preflop\캐시게임_전략요약.md`, 저작권상 레포 미포함).
      적용 후보: ①플랍 텍스처 분류(마른/젖은/페어드/A하이)별 c-bet ②핸드 강도 5분류별
      스트리트 전략 ③3벳 = 상대 오픈 레인지 상위 1/4(밸류)+그 절반(라이트) 규칙
      ④SPR·팟 컨트롤 ⑤베팅 사이즈 규칙 ⑥리버 씬 밸류/블러프 ⑦상대 모델링 수치
      (VPIP/PFR/F3B/AF/WtSD 등 — StatsService 확장)와 유형별 착취(니트/콜스테이션/LAG)
      배치: bot·stats  |  완료기준: BotBrain 포스트플랍 판단이 요약 규칙 기반 + 테스트  |  참고: PROMPTS.md

## 💡 아이디어 (Backlog — 확정 안 됨)

- [x] 프리플랍 레인지 오버레이 — 정적 JSON(6-max 100bb RFI), `GET /api/preflop-range`, 히어로 핸드 오픈 판정
- [x] 3인 이상 테이블 UI — 좌석 동적 렌더(flex-wrap)로 2~6인 지원, 타임뱅크 카운트다운 표시
- [x] 관전 모드 — `GET /api/tables/{id}/spectate`, 착석 없이 구경(홀카드 전부 숨김)
- [~] 배포: AWS Lightsail(2GB) — **준비 완료**(Dockerfile·prod 프로파일·이퀴티 반복 외부화·DEPLOY.md).
      실제 라이브 배포 실행만 남음(AWS 계정·SSH 자격증명 필요 → 사용자가 [DEPLOY.md](DEPLOY.md) 따라 실행)

---

## ✅ 완료 (로드맵)

- [x] 게임 엔진 도메인 + 단위테스트 (콘솔/테스트로 한 판 끝까지) — `HandEngine`, `eval`, 300판 칩보존 속성 테스트
- [x] STOMP 연동 + React 테이블 UI (2인용) — 개인화 리댁션 뷰, WebSocket 통합테스트
- [x] RuleGuard — 최소 바이인 / 재입장 쿨다운 / 일일 리로드 한도
- [x] 이퀴티 오버레이 — 몬테카를로, 본인 이퀴티만 push
- [x] StatsService + ROI 리더보드 — VPIP/PFR/net, `GET /api/leaderboard`
- [x] 핸드 히스토리 리플레이 — 이벤트 소싱(`HandLog`)으로 지난 판 프레임 단위 되감기, `GET /api/tables/{id}/hands`
- [x] 멀티 테이블 / 로비 — `GET /api/tables`로 활성 테이블 목록(좌석수·진행상태)
- [x] 타임뱅크 — 액션 30초 제한, 초과 시 자동 체크/폴드(`TurnTimer`+스케줄러), 뷰에 남은 초 노출
- [x] 통계 영속화 — JSON 파일 스냅샷(`StatsStore`/`JsonFileStatsStore`), 재시작 후 리더보드 복원
- [x] 핸드 자동 복기(EV 손실 기반 실수 감지) — `review` 패키지(`HandReviewer`), 이퀴티 vs 팟오즈로
      콜/폴드 지점 EV 손실 수치화 + 핸드당 최대 실수 1개. `GET /api/tables/{id}/hands/{index}/review`,
      세션 누적 `GET /api/tables/{id}/review/session`, 프론트 복기 패널(리플레이 되감기 + 실수 마커)
- [x] 검증 가능한 셔플(commit-reveal) — `fairness` 패키지(`CommittedShuffle`), 딜 전 SHA-256 커밋 공개 →
      종료 후 솔트+덱 리빌. `GET /api/tables/{id}/fairness`, 테이블 커밋 배지 + 복기 패널에서
      브라우저(crypto.subtle) 해시 재계산 검증. 진행 중 핸드의 덱은 절대 리빌하지 않음
- [x] AI 상대 — `bot` 패키지(`BotBrain`: 이퀴티 vs 팟오즈 판단 = 복기와 동일 철학, `BotService`+스위퍼).
      `POST/DELETE /api/tables/{id}/bots`, 봇 차례 자동 액션(생각 지연 `poker.bot.think-ms`, 기본 900ms),
      프론트 "🤖 AI 상대 추가" 버튼. 사람 혼자 + AI 로 실전 연습 가능
- [x] 전지적 관찰자 시점 — `GET /api/tables/{id}/godview`(폴드 포함 전 홀카드 공개).
      내가 플레이 중이 아닐 때(폴드/미착석/핸드 종료)만 "👁 상대 패 보기" 버튼 노출, 명시적 토글로만 공개
- [x] 베팅금액 수동 입력 수정 — 입력칸이 최소 금액으로 강제 복원돼 수정 불가능하던 버그.
      빈칸 허용 + placeholder 안내, 전송 시에만 빈 값을 최소 레이즈로 대체, 버튼에 전송 금액 표시
- [x] 자동 다음 핸드 진행 — `AutoDealService`(+스위퍼): 핸드 종료 후 딜레이(기본 4초,
      `poker.autodeal.delay-ms`) 뒤 자동 시작, 칩 보유 2명 미만이면(버스트) 중단, 첫 핸드는 수동.
      `GET/PUT /api/tables/{id}/autodeal` + 프론트 "▶ 자동진행 ON/OFF" 토글
- [x] 봇 행동 이유 표시 — `BotBrain.Decision`에 reason(예: "이퀴티 63% ≫ 필요 8% + 마진 → 팟 레이즈"),
      `GET /api/tables/{id}/bots/reasons`(진행 중 핸드는 기본 숨김, god=true 는 godview 와 동일 신뢰),
      프론트 "🧠 AI 판단 로그" 패널
- [x] 홈(랜딩) 페이지 분리 — 첫 접속 시 홈(타이틀·기능 소개·착석 폼·저장된 플레이어·테이블 로비),
      착석하면 자동 입장, 테이블 헤더 "🏠 홈" 버튼으로 왕복(연결·게임 상태 유지)
- [x] 봇 프리플랍 차트 연동 — 60p 프리플랍 교재(개인 소장 PDF)를 픽셀 판독으로 전사(차트 43개,
      각 차트의 인쇄된 콤보 수로 전량 검산). `range/BtsPreflopCharts`(JSON 없으면 자동 비활성) +
      `bot/PreflopAdvisor`(레이즈 수로 오픈/3벳/BB방어/스퀴즈/4벳/5벳 판별, 포지션 자동 배정,
      경계선 핸드는 `poker.bot.borderline-freq` 빈도 혼합, 셀 분할 = 콤보 비율 확률 실행).
      차트 밖 상황·포스트플랍은 기존 이퀴티 폴백. AI 판단 로그에 "차트: ..." 근거 표기.
      ⚠ 전사 JSON·요약 문서는 저작권상 gitignore(로컬 + C:\preflop 백업만)

> 현재 전체 116개 단위·통합 테스트 통과(차트 스모크는 로컬 전용 — 파일 없으면 자동 스킵).
> 배포 준비물(Docker/prod/DEPLOY.md) 완비.

---

## 📌 작업 항목 템플릿 (새 항목 복사용)

```
- [ ] **<작업명>** — <한 줄 목적>
      배치: <패키지>  |  완료기준: <테스트/문서>  |  참고: PROMPTS.md
```
