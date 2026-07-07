# CLAUDE.md

## 🎯 1. 프로젝트 단기 기억 (항상 로드)
- **가이드라인**: 코딩 규칙은 항상 @RULES.md를 바탕으로 판단할 것.
- **질문 양식**: 사용자가 요청 시 @PROMPTS.md의 작업별(기능추가/버그수정 등) 템플릿 구조를 최우선으로 준수할 것.
- **진행 상황**: 작업 완료 후에는 항상 @TODO.md의 체크박스를 업데이트할 것.

## 📉 2. 1인 개발 토큰 절약 규칙
1. 코드를 작성하거나 수정할 때, 배경 원리나 개념 설명은 모두 생략하고 오직 **수정된 코드(Edit)와 결과**만 출력하라.
2. 사용자가 직접 묻지 않는 한 `PROJECT.md`나 `ARCHITECTURE.md` 전체를 매번 새로 분석하며 토큰을 낭비하지 마라.
3. 답변은 결론부터. 서론·요약·"~하겠습니다" 류의 군더더기 없이 실행하고, 끝난 뒤 한 줄로 결과만 보고하라.
4. 이미 대화에서 확정된 사실·결정을 다시 설명하거나 재검토하지 마라.
5. 파일을 방금 수정했으면 확인용으로 다시 읽지 마라(Edit이 실패하면 알려준다).
6. 넓은 탐색이 필요할 때만 서브에이전트/Explore를 쓰고, 좁은 확인은 직접 Grep/Read로 끝내라.

## ⚡ 3. 자주 쓰는 명령어 (재탐색 금지)
- 테스트: `./gradlew test`
- 실행: `./gradlew bootRun` (→ http://localhost:8082, `server.port`는 application.properties)
- 순수 엔진 콘솔 한 판: `./gradlew runDemo --args="42"`
- 프론트 빌드: `cd frontend && npm run build` (산출물 → `src/main/resources/static`)
- 프론트 개발: `cd frontend && npm run dev` (Vite :5173, `/ws`는 :8080 프록시)
- 스택: Java 21 / Spring Boot 4.1 / STOMP+SockJS / React+Vite

## 🧱 4. 절대 철칙 (어기면 버그, 상세는 @ARCHITECTURE.md)
- `engine` 패키지는 순수 도메인 — Spring/WebSocket/DB 타입 import 금지.
- 의존 방향은 아래로만: `web → table/rule/equity/stats → engine`.
- 홀카드는 `dto/*View` 리댁션 후에만 전송. 도메인 객체 직접 브로드캐스트 금지.
- 액션 주체는 `principal.getName()`만 신뢰(요청 body의 playerId 불신).
- 동시성 경계는 `Table`이 전담 — 엔진에 락 넣지 않는다.

## ✅ 5. 작업 마무리 규칙
- 커밋 전 `./gradlew test` 통과 필수. 깨진 채 커밋 금지.
- 의미 단위로 커밋·푸시는 **묻지 말고 자동 진행**(표준 승인).
- 커밋 메시지·주석·답변은 한국어. 커밋 메시지 끝에 Co-Authored-By 라인 유지.
- 로드맵/기능 상태가 바뀌면 @TODO.md 체크박스와 필요 문서를 함께 갱신.
