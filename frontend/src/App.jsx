import React, { useState } from 'react';
import { usePokerSocket } from './usePokerSocket.js';

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

function Login({ onLogin }) {
  const [id, setId] = useState('');
  const [name, setName] = useState('');
  return (
    <div className="login">
      <h1>홈포커 테이블 t1</h1>
      <p>두 개의 브라우저 탭에서 서로 다른 ID로 접속해 2인 홀덤을 해보세요.</p>
      <input placeholder="플레이어 ID (예: alice)" value={id} onChange={(e) => setId(e.target.value)} />
      <input placeholder="표시 이름" value={name} onChange={(e) => setName(e.target.value)} />
      <button disabled={!id} onClick={() => onLogin(id.trim(), name.trim())}>착석</button>
    </div>
  );
}

export default function App() {
  const [creds, setCreds] = useState(null);
  const { connected, state, error, startHand, act } = usePokerSocket(creds?.id, creds?.name);

  if (!creds) return <Login onLogin={(id, name) => setCreds({ id, name })} />;

  return (
    <div className="app">
      <header>
        <span className={`dot ${connected ? 'on' : 'off'}`} />
        <span>{creds.id} · 테이블 t1</span>
        <button className="ghost" onClick={startHand}>새 핸드 시작</button>
      </header>

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
          </div>

          <div className="seats">
            {state.seats.map((s) => (
              <Seat key={s.playerId} seat={s} isViewer={s.playerId === creds.id} />
            ))}
          </div>

          {state.handInProgress && <ActionBar state={state} act={act} />}

          {!state.handInProgress && Object.keys(state.payouts || {}).length > 0 && (
            <div className="result">
              <b>결과</b> {Object.entries(state.payouts).map(([id, amt]) => `${id}: +${amt}`).join(' · ')}
            </div>
          )}
        </main>
      )}
    </div>
  );
}

function translateStreet(s) {
  return { WAITING: '대기', PREFLOP: '프리플랍', FLOP: '플랍', TURN: '턴', RIVER: '리버', SHOWDOWN: '쇼다운', COMPLETE: '종료' }[s] || s;
}
