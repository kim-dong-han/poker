import React, { useEffect, useState } from 'react';
import { usePokerTable } from './usePokerTable.js';

const SUIT = { s: '♠', h: '♥', d: '♦', c: '♣' };

/* ------------------------------------------------------------------ 저장된 플레이어(localStorage) */
const SAVED_KEY = 'homepoker.players';
function loadSaved() {
  try { return JSON.parse(localStorage.getItem(SAVED_KEY)) || []; } catch { return []; }
}
function persistSaved(list) {
  try { localStorage.setItem(SAVED_KEY, JSON.stringify(list)); } catch { /* 무시 */ }
}

/* ------------------------------------------------------------------ 카드 */
function Card({ code, faceDown, delay = 0, flip }) {
  if (faceDown || !code) {
    return <span className="card back" style={{ animationDelay: `${delay}ms` }} />;
  }
  const rank = code.slice(0, -1);
  const suit = code.slice(-1);
  const red = suit === 'h' || suit === 'd';
  return (
    <span className={`card ${red ? 'red' : 'black'} ${flip ? 'flip' : ''}`}
      style={{ animationDelay: `${delay}ms` }}>
      <b>{rank}</b>
      <span className="pip">{SUIT[suit] || suit}</span>
    </span>
  );
}

/* ------------------------------------------------------------------ 카운트다운 링 */
function TimerRing({ actorId, seconds, total = 30 }) {
  const [left, setLeft] = useState(seconds ?? 0);
  useEffect(() => {
    setLeft(seconds ?? 0);
    const t = setInterval(() => setLeft((s) => (s > 0 ? s - 1 : 0)), 1000);
    return () => clearInterval(t);
  }, [actorId, seconds]);
  const r = 20;
  const circ = 2 * Math.PI * r;
  const frac = Math.max(0, Math.min(1, left / total));
  const urgent = left <= 5;
  return (
    <span className={`timer-ring ${urgent ? 'urgent' : ''}`}>
      <svg viewBox="0 0 48 48" width="48" height="48">
        <circle cx="24" cy="24" r={r} className="ring-bg" />
        <circle cx="24" cy="24" r={r} className="ring-fg"
          strokeDasharray={circ} strokeDashoffset={circ * (1 - frac)}
          transform="rotate(-90 24 24)" />
      </svg>
      <span className="timer-num">{left}</span>
    </span>
  );
}

/* ------------------------------------------------------------------ 좌석(테이블 둘레에 배치) */
function Seat({ seat, isViewer, pos, secondsLeft, actorId, isWinner }) {
  const hidden = !seat.holeCards;
  const cards = seat.holeCards
    ? seat.holeCards.map((c, i) => <Card key={i} code={c} flip delay={i * 90} />)
    : (seat.status !== 'FOLDED' ? [<Card key="a" faceDown />, <Card key="b" faceDown />] : null);
  return (
    <div className={`seat ${seat.currentActor ? 'acting' : ''} ${seat.status === 'FOLDED' ? 'folded' : ''} ${isWinner ? 'winner' : ''}`}
      style={{ left: `${pos.x}%`, top: `${pos.y}%` }}>
      {seat.currentActor && actorId && (
        <div className="seat-timer"><TimerRing actorId={actorId} seconds={secondsLeft} /></div>
      )}
      <div className="seat-cards">{cards}</div>
      <div className="seat-plate">
        <div className="seat-name">
          {seat.button && <span className="dealer">D</span>}
          {seat.name}{isViewer ? ' (나)' : ''}
        </div>
        <div className="seat-stack"><span className="chip-ico" />{seat.stack}</div>
      </div>
      {seat.committedThisStreet > 0 && (
        <div className={`seat-bet bet-${betSide(pos)}`}><span className="chip-ico sm" />{seat.committedThisStreet}</div>
      )}
      {seat.status === 'FOLDED' && <div className="fold-tag">FOLD</div>}
    </div>
  );
}

// 좌석 위치에 따라 베팅 칩을 테이블 중앙 쪽으로 살짝 붙인다.
function betSide(pos) {
  if (pos.y > 60) return 'up';
  if (pos.y < 40) return 'down';
  return pos.x < 50 ? 'right' : 'left';
}

/* N인 좌석을 타원 둘레에 배치. index 0(=뷰어)이 하단 중앙, 시계방향. */
function seatPositions(n) {
  const rx = 44, ry = 38, cx = 50, cy = 50;
  const out = [];
  for (let k = 0; k < n; k++) {
    const theta = (Math.PI / 2) + (k * 2 * Math.PI) / n; // 하단에서 시작
    out.push({ x: cx + rx * Math.cos(theta), y: cy + ry * Math.sin(theta) });
  }
  return out;
}

/* ------------------------------------------------------------------ 액션바 */
function ActionBar({ state, act }) {
  const legal = new Set(state.viewerLegalActions || []);
  const [amount, setAmount] = useState('');
  const canRaise = legal.has('RAISE');
  const canBet = legal.has('BET');
  const defaultTo = state.viewerMinRaiseTo || 0;
  const amt = amount === '' ? defaultTo : Number(amount);

  if (legal.size === 0) return null;
  return (
    <div className="actionbar">
      {legal.has('FOLD') && <button className="act fold" onClick={() => act('FOLD')}>폴드</button>}
      {legal.has('CHECK') && <button className="act check" onClick={() => act('CHECK')}>체크</button>}
      {legal.has('CALL') && <button className="act call" onClick={() => act('CALL')}>콜 <b>{state.viewerToCall}</b></button>}
      {(canBet || canRaise) && (
        <span className="raise-group">
          <input type="number" value={amt} onChange={(e) => setAmount(e.target.value)} />
          {canBet && <button className="act raise" onClick={() => act('BET', amt)}>벳</button>}
          {canRaise && <button className="act raise" onClick={() => act('RAISE', amt)}>레이즈 to</button>}
        </span>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ 리더보드 */
function Leaderboard() {
  const [rows, setRows] = useState([]);
  useEffect(() => {
    const load = () => fetch('/api/leaderboard').then((r) => r.json()).then(setRows).catch(() => {});
    load();
    const t = setInterval(load, 4000);
    return () => clearInterval(t);
  }, []);
  if (rows.length === 0) return null;
  return (
    <div className="leaderboard">
      <b>ROI 리더보드</b>
      <table>
        <thead>
          <tr><th>#</th><th>플레이어</th><th>net</th><th>핸드</th><th>승</th><th>VPIP</th><th>PFR</th></tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r.playerId}>
              <td>{i + 1}</td>
              <td>{r.name || r.playerId}</td>
              <td className={r.netProfit >= 0 ? 'pos' : 'neg'}>{r.netProfit >= 0 ? '+' : ''}{r.netProfit}</td>
              <td>{r.handsPlayed}</td>
              <td>{r.handsWon}</td>
              <td>{r.vpip}%</td>
              <td>{r.pfr}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/* ------------------------------------------------------------------ 검증 가능한 셔플(commit-reveal) */
// 서버와 동일 규칙: SHA-256(salt + ":" + 덱 표기를 ,로 연결) 소문자 hex. 비보안 컨텍스트면 null.
async function sha256Hex(text) {
  if (!window.crypto?.subtle) return null;
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

function ShuffleProofBox({ proof }) {
  const [verified, setVerified] = useState(null); // null=검증불가/대기, true/false=결과
  useEffect(() => {
    setVerified(null);
    if (!proof) return;
    sha256Hex(`${proof.salt}:${proof.deckOrder.join(',')}`)
      .then((h) => setVerified(h === null ? null : h === proof.commitment))
      .catch(() => setVerified(null));
  }, [proof]);
  if (!proof) return null;
  return (
    <div className="fair-proof">
      🔒 검증 가능한 셔플 — 시작 전 커밋 <code>{proof.commitment.slice(0, 16)}…</code>
      {verified === true && <span className="verify ok">✓ 브라우저 재계산 해시 일치 — 진행 중 덱 조작 없음</span>}
      {verified === false && <span className="verify bad">✗ 해시 불일치 — 셔플이 커밋과 다름!</span>}
      {verified === null && <span className="verify muted">브라우저 해시 검증 불가(HTTPS/localhost 필요)</span>}
      <details>
        <summary>공개값(솔트·덱 순서) 보기</summary>
        <div className="mono">salt: {proof.salt}</div>
        <div className="mono">deck: {proof.deckOrder.join(',')}</div>
      </details>
    </div>
  );
}

/* ------------------------------------------------------------------ 핸드 복기(리플레이 + EV 손실 실수 마커) */
const pct = (x) => Math.round(x * 100);

function translateMistakeType(t) {
  const [street, action] = t.split(' ');
  return `${translateStreet(street)} ${action === 'CALL' ? '오버콜' : '오버폴드'}`;
}

function ReplayPanel({ onClose }) {
  const [hands, setHands] = useState([]);
  const [sel, setSel] = useState(null);
  const [frames, setFrames] = useState([]);
  const [review, setReview] = useState(null);
  const [session, setSession] = useState([]);
  const [fairness, setFairness] = useState(null);
  const [step, setStep] = useState(0);

  useEffect(() => {
    fetch('/api/tables/t1/hands').then((r) => r.json()).then(setHands).catch(() => {});
    fetch('/api/tables/t1/review/session').then((r) => r.json()).then(setSession).catch(() => {});
    fetch('/api/tables/t1/fairness').then((r) => r.json()).then(setFairness).catch(() => {});
  }, []);

  const open = (i) => {
    setSel(i); setStep(0); setFrames([]); setReview(null);
    fetch(`/api/tables/t1/hands/${i}`).then((r) => r.json()).then(setFrames).catch(() => {});
    fetch(`/api/tables/t1/hands/${i}/review`).then((r) => r.json()).then(setReview).catch(() => {});
  };

  const frame = frames[step];
  // 액션 인덱스 d.step 의 결과가 반영된 프레임은 step+1 → 그 프레임에 실수 마커를 찍는다.
  const mistakeAt = {};
  (review?.decisions || []).forEach((d) => { if (d.mistake) mistakeAt[d.step + 1] = d; });
  const worst = review?.worstMistake;
  const curMistake = mistakeAt[step];

  return (
    <div className="replay-panel">
      <div className="replay-head">
        <b>핸드 복기</b>
        <span className="muted">이퀴티 vs 팟오즈로 EV 손실 실수 자동 감지</span>
        <button className="ghost sm" onClick={onClose}>닫기 ×</button>
      </div>

      <div className="replay-hands">
        {hands.length === 0 && <span className="muted">완료된 핸드가 없습니다. 한 판 끝내고 다시 열어보세요.</span>}
        {/* API 는 최신 핸드가 index 0 — 그대로 최신부터 보여주고 번호는 시간순으로 붙인다 */}
        {hands.map((h) => (
          <button key={h.index} className={`hand-chip ${sel === h.index ? 'active' : ''}`}
            onClick={() => open(h.index)}>
            #{hands.length - h.index} · {h.players.join('·')}{h.showdown ? ' · 쇼다운' : ''}
          </button>
        ))}
      </div>

      {frame && (
        <div className="replay-body">
          {worst && (
            <div className="mistake-banner worst" onClick={() => setStep(worst.step + 1)}
              title="클릭하면 그 지점으로 이동">
              ⚠ 최대 실수: <b>{worst.playerName}</b> {translateStreet(worst.street)}{' '}
              {worst.action === 'CALL' ? '콜' : '폴드'} — 이퀴티 {pct(worst.equity)}% vs 필요{' '}
              {pct(worst.requiredEquity)}% (<b>-{worst.evLossBb.toFixed(1)}bb</b>)
            </div>
          )}
          {review && !worst && <div className="mistake-banner clean">✔ 이 핸드에서 감지된 실수 없음</div>}

          <div className="replay-stage">
            <div className="replay-street">{translateStreet(frame.street)} · 팟 <b>{frame.pot}</b></div>
            <div className="board">
              {frame.board.length === 0
                ? <span className="board-empty">프리플랍</span>
                : frame.board.map((c) => <Card key={c} code={c} />)}
            </div>
            <div className="replay-seats">
              {frame.seats.map((s) => (
                <div key={s.playerId}
                  className={`replay-seat ${s.status === 'FOLDED' ? 'folded' : ''} ${s.currentActor ? 'acting' : ''}`}>
                  <span className="rname">{s.button && <span className="dealer">D</span>}{s.name}</span>
                  <span className="rcards">{(s.holeCards || []).map((c) => <Card key={c} code={c} />)}</span>
                  <span className="rstack"><span className="chip-ico sm" />{s.stack}</span>
                  {s.committedThisStreet > 0 && <span className="rbet">벳 {s.committedThisStreet}</span>}
                  {s.status === 'FOLDED' && <span className="fold-tag">FOLD</span>}
                </div>
              ))}
            </div>
            <div className="replay-action">{frame.action ? `▶ ${frame.action}` : '핸드 시작(딜·블라인드 직후)'}</div>
            {curMistake && (
              <div className="mistake-banner">
                ⚠ <b>{curMistake.playerName}</b>의 {curMistake.action === 'CALL' ? '콜' : '폴드'}:
                이퀴티 {pct(curMistake.equity)}%, 필요 이퀴티 {pct(curMistake.requiredEquity)}%
                → EV <b>-{curMistake.evLossBb.toFixed(1)}bb</b>
              </div>
            )}
          </div>

          <div className="replay-ctrl">
            <button className="ghost sm" disabled={step === 0} onClick={() => setStep(step - 1)}>◀ 이전</button>
            <div className="timeline">
              {frames.map((f, i) => (
                <button key={i}
                  className={`tstep ${i === step ? 'cur' : ''} ${mistakeAt[i] ? 'bad' : ''}`}
                  title={f.action || '시작'} onClick={() => setStep(i)} />
              ))}
            </div>
            <button className="ghost sm" disabled={step >= frames.length - 1}
              onClick={() => setStep(step + 1)}>다음 ▶</button>
          </div>
          <ShuffleProofBox proof={sel != null ? fairness?.proofs?.[sel] : null} />
          {review && <div className="assumption">{review.assumption}</div>}
        </div>
      )}

      {session.length > 0 && (
        <div className="session-report">
          <b>세션 누적 리포트</b>
          <table>
            <thead>
              <tr><th>플레이어</th><th>판정 지점</th><th>실수</th><th>EV 손실 합</th><th>최다 유형</th></tr>
            </thead>
            <tbody>
              {session.map((r) => (
                <tr key={r.playerId}>
                  <td>{r.playerName || r.playerId}</td>
                  <td>{r.decisions}</td>
                  <td>{r.mistakes}</td>
                  <td className={r.totalEvLossBb > 0 ? 'neg' : 'pos'}>
                    {r.totalEvLossBb > 0 ? `-${r.totalEvLossBb.toFixed(1)}bb` : '0bb'}
                  </td>
                  <td>{r.topMistakeType ? translateMistakeType(r.topMistakeType) : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ 플레이어 추가 + 저장된 플레이어 */
function PlayerAdder({ onAdd, seatedIds, saved, onForget, compact }) {
  const [id, setId] = useState('');
  const [name, setName] = useState('');
  const submit = () => {
    const pid = id.trim();
    if (!pid || seatedIds.includes(pid)) return;
    onAdd(pid, name.trim());
    setId(''); setName('');
  };
  const onEnter = (e) => e.key === 'Enter' && submit();
  const available = saved.filter((s) => !seatedIds.includes(s.id));
  return (
    <div className={compact ? 'add-inline' : 'login'}>
      {!compact && (
        <>
          <h1>♠ 홈포커 <span className="thin">테이블 t1</span></h1>
          <p>혼자서 여러 명을 앉혀 진행하는 로컬 테스트. 플레이어를 추가하세요(2명+면 시작 가능).</p>
        </>
      )}
      {available.length > 0 && (
        <div className="saved-players">
          <span className="saved-label">저장된 플레이어</span>
          {available.map((s) => (
            <span key={s.id} className="saved-chip">
              <button className="seat-quick" onClick={() => onAdd(s.id, s.name)}>+ {s.name}</button>
              <button className="forget" title="목록에서 삭제" onClick={() => onForget(s.id)}>×</button>
            </span>
          ))}
        </div>
      )}
      <div className="add-row">
        <input placeholder="플레이어 ID (예: alice)" value={id}
          onChange={(e) => setId(e.target.value)} onKeyDown={onEnter} />
        <input placeholder="표시 이름(선택)" value={name}
          onChange={(e) => setName(e.target.value)} onKeyDown={onEnter} />
        <button className="primary" disabled={!id.trim()} onClick={submit}>착석</button>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ 앱 */
export default function App() {
  const { players, views, errors, connected, addPlayer, removePlayer, startHand, act } = usePokerTable();
  const [picked, setPicked] = useState(null);
  const [saved, setSaved] = useState(loadSaved);
  const [showReplay, setShowReplay] = useState(false);
  const [commitment, setCommitment] = useState(null);

  // 착석 시 저장 목록에 추가(중복 제거).
  const addAndSave = (id, name) => {
    addPlayer(id, name);
    setSaved((prev) => {
      const next = prev.some((p) => p.id === id) ? prev : [...prev, { id, name: name || id }];
      persistSaved(next);
      return next;
    });
  };
  const forget = (id) => setSaved((prev) => { const n = prev.filter((p) => p.id !== id); persistSaved(n); return n; });

  const seatedIds = players.map((p) => p.id);
  const activeId = picked && seatedIds.includes(picked) ? picked : players[0]?.id;
  const state = activeId ? views[activeId] : null;
  const error = activeId ? errors[activeId] : null;
  const actorId = state?.handInProgress ? state.currentActorId : null;
  const done = state && !state.handInProgress;
  const payouts = state?.payouts || {};

  // 핸드 시작/종료 시 현재 셔플 커밋 해시를 갱신(시작 전 공개 = 조작 불가 증명의 앞단).
  const inProgress = state?.handInProgress ?? false;
  useEffect(() => {
    if (!state) { setCommitment(null); return; }
    fetch('/api/tables/t1/fairness').then((r) => r.json())
      .then((f) => setCommitment(f.currentCommitment)).catch(() => {});
  }, [inProgress]);

  if (players.length === 0) {
    return <PlayerAdder onAdd={addAndSave} seatedIds={seatedIds} saved={saved} onForget={forget} />;
  }

  const positions = state ? seatPositions(state.seats.length) : [];
  // 좌석을 회전시켜 activeId 를 하단(index 0)에 오게 한다.
  let ordered = state ? state.seats : [];
  if (state) {
    const vi = state.seats.findIndex((s) => s.playerId === activeId);
    if (vi > 0) ordered = [...state.seats.slice(vi), ...state.seats.slice(0, vi)];
  }

  return (
    <div className="app">
      <header>
        <span className="logo">♠ 홈포커</span>
        <span className={`dot ${connected[activeId] ? 'on' : 'off'}`} />
        <span className="whoami"><b>{activeId}</b>(으)로 플레이 중</span>
        <span className="hbtns">
          <button className="ghost" onClick={() => setShowReplay((v) => !v)}>
            {showReplay ? '복기 닫기' : '핸드 복기'}
          </button>
          <button className="ghost" onClick={() => startHand(activeId)}>새 핸드 시작</button>
        </span>
      </header>

      {showReplay && <ReplayPanel onClose={() => setShowReplay(false)} />}

      <div className="players-panel">
        <div className="players-head">
          <b>참가자 {players.length}명</b> <span className="muted">— 칩을 눌러 그 사람으로 전환</span>
        </div>
        <div className="player-chips">
          {players.map((p) => (
            <span key={p.id}
              className={`pchip ${p.id === activeId ? 'active' : ''} ${p.id === actorId ? 'toact' : ''}`}
              onClick={() => setPicked(p.id)}>
              <span className={`dot ${connected[p.id] ? 'on' : 'off'}`} />
              {p.name}
              {p.id === actorId && <span className="turn-badge">차례</span>}
              <button className="x" title="퇴장"
                onClick={(e) => { e.stopPropagation(); removePlayer(p.id); }}>×</button>
            </span>
          ))}
        </div>
        <PlayerAdder onAdd={addAndSave} seatedIds={seatedIds} saved={saved} onForget={forget} compact />
      </div>

      {error && <div className="error">⚠ {error}</div>}
      {!state && <div className="muted center">연결 중…</div>}

      {state && (
        <main>
          <div className="table-wrap">
            <div className="poker-table">
              <div className="rail" />
              <div className="felt">
                <div className="table-center">
                  <div className="street-badge">{translateStreet(state.street)}</div>
                  <div className="board">
                    {state.board.length === 0
                      ? <span className="board-empty">— 보드 —</span>
                      : state.board.map((c, i) => <Card key={c} code={c} flip delay={i * 120} />)}
                  </div>
                  <div className="pot"><span className="chip-ico" />팟 <b>{state.pot}</b></div>
                  {state.viewerEquity != null && (
                    <div className="equity" title="내 홀카드 기준 몬테카를로 승률">
                      내 승률 {Math.round(state.viewerEquity * 100)}%
                    </div>
                  )}
                  {commitment && (
                    <div className="fair-badge"
                      title={`셔플 커밋(SHA-256): ${commitment}\n딜 전에 공개된 해시 — 핸드 종료 후 '핸드 복기'에서 솔트·덱이 공개되면 브라우저가 재계산해 검증합니다.`}>
                      🔒 셔플 커밋 {commitment.slice(0, 10)}…
                    </div>
                  )}
                </div>

                {ordered.map((s, i) => (
                  <Seat key={s.playerId} seat={s} pos={positions[i]}
                    isViewer={s.playerId === activeId}
                    secondsLeft={state.turnSecondsLeft} actorId={actorId}
                    isWinner={done && payouts[s.playerId] > 0} />
                ))}
              </div>
            </div>
          </div>

          {state.handInProgress && (
            actorId === activeId
              ? <ActionBar state={state} act={(type, amount) => act(activeId, type, amount)} />
              : <div className="turn-hint">
                  <TimerRing actorId={actorId} seconds={state.turnSecondsLeft} />
                  지금은 <b>{actorId}</b> 차례
                  {actorId && <button className="ghost sm" onClick={() => setPicked(actorId)}>{actorId}(으)로 전환 →</button>}
                </div>
          )}

          {done && Object.keys(payouts).length > 0 && (
            <div className="result">
              🏆 {Object.entries(payouts).filter(([, a]) => a > 0)
                .map(([id, amt]) => `${id} +${amt}`).join(' · ')}
            </div>
          )}

          <Leaderboard />
        </main>
      )}
    </div>
  );
}

function translateStreet(s) {
  return { WAITING: '대기', PREFLOP: '프리플랍', FLOP: '플랍', TURN: '턴', RIVER: '리버', SHOWDOWN: '쇼다운', COMPLETE: '종료' }[s] || s;
}
