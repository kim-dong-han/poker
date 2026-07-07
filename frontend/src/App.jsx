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
        <button className="ghost" onClick={() => startHand(activeId)}>새 핸드 시작</button>
      </header>

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
