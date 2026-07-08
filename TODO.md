# TODO — 작업 목록

작업 관리 보드. 상태는 `[ ]` 대기 · `[~]` 진행중 · `[x]` 완료.
완료된 로드맵은 아래 "완료" 섹션에 보관하고, 위쪽에서 현재/다음 작업을 관리한다.

---

## 🔥 지금 (In Progress)

- [ ] **베팅금액 수동 입력 수정** — 레이즈 입력칸이 기본값(minRaiseTo, 예: 2)으로 되돌아가 지워지지/수정되지 않는 문제.
      배치: frontend `App.jsx` ActionBar (`value={amt}` → `value={amount}` + placeholder)  |  완료기준: 빈칸 허용·직접 입력한 금액으로 벳/레이즈 동작

## ⏭️ 다음 (Next — 우선순위 순)

- [ ] **자동 다음 핸드 진행** — 매번 "새 핸드 시작" 버튼 누르지 않아도, 핸드 종료 후 자동으로 다음 핸드 시작.
      누군가 칩이 0이 되면(버스트) 자동 진행 중단. 배치: `table`(핸드 종료 스케줄링, 딜레이 수 초)  |  완료기준: 통합테스트 + 프론트 토글(자동/수동)
- [ ] **봇 행동 이유 표시** — 봇이 왜 체크/콜/레이즈/폴드했는지 근거 노출. 외부 API 아님 —
      `BotBrain`이 이미 계산하는 값(이퀴티 %, 필요이퀴티=팟오즈, 어떤 임계값 규칙에 걸렸는지)을
      `Decision`에 reason 필드로 담아 뷰/채팅 라인에 표시(핸드 종료 후 또는 관전 시에만 공개해 정보 유출 방지).
      배치: `bot` + `dto`  |  완료기준: 봇 액션마다 "이퀴티 71% > 필요 24%+25% → 레이즈" 식 설명
- [ ] **홈(랜딩) 페이지 분리** — 처음 접속 시 홈 화면(타이틀·닉네임/입장·테이블 로비), 플레이 화면과 분리.
      메인(플레이) 페이지에서 홈으로 돌아가는 버튼 추가. 배치: frontend (라우팅 — 상태 기반 화면 전환 또는 react-router)  |  완료기준: 홈 ↔ 테이블 왕복 이동

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

> 현재 전체 99개 단위·통합 테스트 통과. 배포 준비물(Docker/prod/DEPLOY.md) 완비.

---

## 📌 작업 항목 템플릿 (새 항목 복사용)

```
- [ ] **<작업명>** — <한 줄 목적>
      배치: <패키지>  |  완료기준: <테스트/문서>  |  참고: PROMPTS.md
```
