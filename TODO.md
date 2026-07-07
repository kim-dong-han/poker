# TODO — 작업 목록

작업 관리 보드. 상태는 `[ ]` 대기 · `[~]` 진행중 · `[x]` 완료.
완료된 로드맵은 아래 "완료" 섹션에 보관하고, 위쪽에서 현재/다음 작업을 관리한다.

---

## 🔥 지금 (In Progress)

- [ ] <현재 잡고 있는 작업 — 하나만 두는 걸 권장>

## ⏭️ 다음 (Next — 우선순위 순)

- (없음 — Next 로드맵 전부 완료. 아래 백로그에서 승격)

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

> 현재 전체 74개 단위·통합 테스트 통과. 배포 준비물(Docker/prod/DEPLOY.md) 완비.

---

## 📌 작업 항목 템플릿 (새 항목 복사용)

```
- [ ] **<작업명>** — <한 줄 목적>
      배치: <패키지>  |  완료기준: <테스트/문서>  |  참고: PROMPTS.md
```
