import React, { useState } from 'react';
import { usePokerTable } from './usePokerTable.js';

const SUIT = { s: '♠', h: '♥', d: '♦', c: '♣' };

function Card({ code }) {
  if (!code) return <span className="card back" />;
  const rank = code.slice(0, -1);
  const suit = code.slice(-1);
  const red = suit === 'h' || suit === 'd';
  return (
    <span className={`card ${red ? 'red' : 'black'}`}>
      <b>{rank}</b>
      <span>{SUIT[suit] || suit}</span>
    </span>
  );
}

function Seat({ seat, isViewer }) {
  const hidden = !seat.holeCards;
  return (
    <div className={`seat ${seat.currentActor ? 'acting' : ''} ${seat.status === 'FOLDED' ? 'folded' : ''}`}>
      <div className="seat-head">
        {seat.button && <span className="dealer">D</span>}
        <span className="seat-name">{seat.name}{isViewer ? ' (나)' : ''}</span>
      </div>
      <div className="seat-cards">
        {seat.holeCards
          ? seat.holeCards.map((c, i) => <Card key={i} code={c} />)
          : (seat.status !== 'FOLDED' ? [<Card key="a" />, <Card key="b" />] : null)}
      </div>
      <div className="seat-foot">
        <span className="stack">{seat.stack}</span>
        {seat.committedThisStreet > 0 && <span className="bet">벳 {seat.committedThisStreet}</span>}
        <span className="status">{seat.status}</span>
      </div>
    </div>
  );
}

function Countdown({ actorId, seconds }) {
  // 서버가 준 남은 초에서 시작해 로컬로 1초씩 깎는다. 액션자나 값이 바뀌면 리셋.
  const [left, setLeft] = useState(seconds);
  React.useEffect(() => {
    setLeft(seconds);
    const t = setInterval(() => setLeft((s) => (s > 0 ? s - 1 : 0)), 1000);
    return () => clearInterval(t);
  }, [actorId, seconds]);
  if (seconds == null || seconds <= 0) return null;
  return <span className={`turn-timer ${left <= 5 ? 'urgent' : ''}`}>⏱ {left}s</span>;
}

function ActionBar({ state, act }) {
  const legal = new Set(state.viewerLegalActions || []);
  const [amount, setAmount] = useState('');
  const canRaise = legal.has('RAISE');
  const canBet = legal.has('BET');
  const defaultTo = state.viewerMinRaiseTo || 0;
  const amt = amount === '' ? defaultTo : Number(amount);

  if (legal.size === 0) {
    return <div className="actionbar muted">당신 차례가 아닙니다…</div>;
  }
  return (
    <div className="actionbar">
      {legal.has('FOLD') && <button onClick={() => act('FOLD')}>폴드</button>}
      {legal.has('CHECK') && <button onClick={() => act('CHECK')}>체크</button>}
      {legal.has('CALL') && <button onClick={() => act('CALL')}>콜 {state.viewerToCall}</button>}
      {(canBet || canRaise) && (
        <span className="raise-group">
          <input
            type="number"
            value={amt}
            onChange={(e) => setAmount(e.target.value)}
          />
          {canBet && <button onClick={() => act('BET', amt)}>벳</button>}
          {canRaise && <button onClick={() => act('RAISE', amt)}>레이즈 to</button>}
        </span>
      )}
    </div>
  );
}

function Leaderboard() {
  const [rows, setRows] = useState([]);
  React.useEffect(() => {
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

function PlayerAdder({ onAdd, seatedIds, compact }) {
  const [id, setId] = useState('');
  const [name, setName] = useState('');
  const submit = () => {
    const pid = id.trim();
    if (!pid || seatedIds.includes(pid)) return;
    onAdd(pid, name.trim());
    setId('');
    setName('');
  };
  const onEnter = (e) => e.key === 'Enter' && submit();
  return (
    <div className={compact ? 'add-inline' : 'login'}>
      {!compact && (
        <>
          <h1>홈포커 테이블 t1 · 로컬 테스트</h1>
          <p>혼자서 여러 명을 앉혀 진행할 수 있어요. 플레이어를 추가하세요(2명 이상이면 핸드 시작 가능).</p>
        </>
      )}
      <input placeholder="플레이어 ID (예: alice)" value={id}
        onChange={(e) => setId(e.target.value)} onKeyDown={onEnter} />
      <input placeholder="표시 이름(선택)" value={name}
        onChange={(e) => setName(e.target.value)} onKeyDown={onEnter} />
      <button disabled={!id.trim()} onClick={submit}>착석</button>
    </div>
  );
}

export default function App() {
  const { players, views, errors, connected, addPlayer, removePlayer, startHand, act } = usePokerTable();
  const [picked, setPicked] = useState(null);

  const seatedIds = players.map((p) => p.id);
  // 지금 조종 중인(=뷰 기준) 플레이어. 고른 사람이 없거나 빠졌으면 첫 플레이어로.
  const activeId = picked && seatedIds.includes(picked) ? picked : players[0]?.id;
  const state = activeId ? views[activeId] : null;
  const error = activeId ? errors[activeId] : null;
  const actorId = state?.handInProgress ? state.currentActorId : null;

  if (players.length === 0) {
    return <PlayerAdder onAdd={addPlayer} seatedIds={seatedIds} />;
  }

  return (
    <div className="app">
      <header>
        <span className={`dot ${connected[activeId] ? 'on' : 'off'}`} />
        <span>테이블 t1 · <b>{activeId}</b>(으)로 플레이 중</span>
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
        <PlayerAdder onAdd={addPlayer} seatedIds={seatedIds} compact />
      </div>

      {error && <div className="error">⚠ {error}</div>}

      {!state && <div className="muted">연결 중…</div>}

      {state && (
        <main>
          <div className="board-area">
            <div className="street">{translateStreet(state.street)}</div>
            <div className="board">
              {state.board.length === 0
                ? <span className="muted">보드 없음</span>
                : state.board.map((c, i) => <Card key={i} code={c} />)}
            </div>
            <div className="pot">팟 {state.pot}</div>
            {actorId && (
              <div className="turn-line">
                차례: <b>{actorId}</b>
                <Countdown actorId={actorId} seconds={state.turnSecondsLeft} />
                {actorId !== activeId && (
                  <button className="ghost sm" onClick={() => setPicked(actorId)}>
                    {actorId}(으)로 전환 →
                  </button>
                )}
              </div>
            )}
            {state.viewerEquity != null && (
              <div className="equity" title="내 홀카드 기준 몬테카를로 승률">
                내 이퀴티 {Math.round(state.viewerEquity * 100)}%
              </div>
            )}
          </div>

          <div className="seats">
            {state.seats.map((s) => (
              <Seat key={s.playerId} seat={s} isViewer={s.playerId === activeId} />
            ))}
          </div>

          {state.handInProgress && (
            actorId === activeId
              ? <ActionBar state={state} act={(type, amount) => act(activeId, type, amount)} />
              : <div className="actionbar muted">
                  {activeId}는 대기 중 — 지금은 <b>{actorId}</b> 차례입니다
                  {actorId && <button className="ghost sm" onClick={() => setPicked(actorId)}>전환</button>}
                </div>
          )}

          {!state.handInProgress && Object.keys(state.payouts || {}).length > 0 && (
            <div className="result">
              <b>결과</b> {Object.entries(state.payouts).map(([id, amt]) => `${id}: +${amt}`).join(' · ')}
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
