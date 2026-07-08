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
  const sym = SUIT[suit] || suit;
  return (
    <span className={`card ${red ? 'red' : 'black'} ${flip ? 'flip' : ''}`}
      data-suit={sym} style={{ animationDelay: `${delay}ms` }}>
      <b>{rank}</b>
      <span className="pip">{sym}</span>
    </span>
  );
}

/* ------------------------------------------------------------------ 아바타 — id 해시로 색을 고정 배정 */
function hueOf(id) {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
  return h % 360;
}
function Avatar({ id, name, mini }) {
  const isBot = id.startsWith('ai-');
  const hue = hueOf(id);
  const style = isBot
    ? { background: 'linear-gradient(160deg, #55617a, #2c3547)' }
    : { background: `linear-gradient(160deg, hsl(${hue} 62% 52%), hsl(${(hue + 40) % 360} 65% 34%))` };
  const label = isBot ? '🤖' : (name || id).trim().charAt(0).toUpperCase();
  return <span className={mini ? 'mini-avatar' : 'avatar'} style={style}>{label}</span>;
}

/* ------------------------------------------------------------------ 칩 스택 — 금액을 칩 색으로 환산 */
const DENOMS = [
  [1000, 'd1000'], [500, 'd500'], [100, 'd100'], [25, 'd25'], [5, 'd5'], [1, 'd1'],
];
function chipsFor(amount, cap = 4) {
  const out = [];
  let left = amount;
  for (const [v, cls] of DENOMS) {
    while (left >= v && out.length < cap) { out.push(cls); left -= v; }
    if (out.length >= cap) break;
  }
  if (out.length === 0) out.push('d1');
  return out;
}
function ChipStack({ amount, cap }) {
  return (
    <span className="chip-stack">
      {chipsFor(amount, cap).map((cls, i) => <span key={i} className={`chip ${cls}`} />)}
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
function Seat({ seat, isViewer, pos, secondsLeft, actorId, isWinner, winAmount, spied }) {
  const cards = seat.holeCards
    ? seat.holeCards.map((c, i) => <Card key={i} code={c} flip delay={i * 90} />)
    : (seat.status !== 'FOLDED' ? [<Card key="a" faceDown />, <Card key="b" faceDown />] : null);
  return (
    <div className={`seat ${seat.currentActor ? 'acting' : ''} ${seat.status === 'FOLDED' ? 'folded' : ''} ${isWinner ? 'winner' : ''} ${spied ? 'spied' : ''}`}
      style={{ left: `${pos.x}%`, top: `${pos.y}%` }}>
      {seat.currentActor && actorId && (
        <div className="seat-timer"><TimerRing actorId={actorId} seconds={secondsLeft} /></div>
      )}
      {isWinner && winAmount > 0 && <div className="win-amount">+{winAmount}</div>}
      {spied && <div className="spy-tag" title="전지적 관찰자 시점으로 공개된 카드">👁</div>}
      <div className="seat-cards">{cards}</div>
      <div className="seat-plate">
        {seat.button && <span className="dealer" title="딜러 버튼">D</span>}
        <Avatar id={seat.playerId} name={seat.name} />
        <div className="plate-info">
          <div className="seat-name">{seat.name}{isViewer ? ' (나)' : ''}</div>
          <div className="seat-stack">{seat.stack.toLocaleString()}</div>
        </div>
      </div>
      {seat.committedThisStreet > 0 && (
        <div className={`seat-bet bet-${betSide(pos)}`}>
          <ChipStack amount={seat.committedThisStreet} cap={3} />
          {seat.committedThisStreet.toLocaleString()}
        </div>
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

/* ------------------------------------------------------------------ 액션바(퀵 사이즈 + 슬라이더) */
function ActionBar({ state, mySeat, act }) {
  const legal = new Set(state.viewerLegalActions || []);
  const [amount, setAmount] = useState('');
  const canRaise = legal.has('RAISE');
  const canBet = legal.has('BET');
  const minTo = state.viewerMinRaiseTo || 0;
  const toCall = state.viewerToCall || 0;
  const committed = mySeat?.committedThisStreet || 0;
  // 올인 상한: 내 스택 + 이번 스트리트에 이미 넣은 금액(레이즈 to 표기 기준)
  const maxTo = mySeat ? mySeat.stack + committed : minTo;
  // 입력칸은 자유롭게 지우고 다시 쓸 수 있어야 한다 — 빈 값이면 전송 시에만 최소 금액으로 대체.
  const amt = amount === '' ? minTo : Number(amount);
  const clamped = Math.max(minTo, Math.min(maxTo, amt));
  const submit = (type) => { act(type, clamped); setAmount(''); };

  // 팟 비율 퀵 사이즈: 콜을 받은 뒤 팟 기준(팟 레이즈 공식 근사).
  const quick = (frac) => {
    const potAfterCall = state.pot + toCall;
    const target = canRaise
      ? committed + toCall + Math.round(potAfterCall * frac)
      : Math.round(state.pot * frac);
    setAmount(String(Math.max(minTo, Math.min(maxTo, target))));
  };
  const fillPct = maxTo > minTo ? ((clamped - minTo) / (maxTo - minTo)) * 100 : 100;

  if (legal.size === 0) return null;
  return (
    <div className="actionbar">
      <div className="ab-buttons">
        {legal.has('FOLD') && (
          <button className="act fold" onClick={() => act('FOLD')}><small>FOLD</small>폴드</button>
        )}
        {legal.has('CHECK') && (
          <button className="act check" onClick={() => act('CHECK')}><small>CHECK</small>체크</button>
        )}
        {legal.has('CALL') && (
          <button className="act call" onClick={() => act('CALL')}>
            <small>CALL</small>콜 <b>{state.viewerToCall.toLocaleString()}</b>
          </button>
        )}
        {canBet && (
          <button className="act raise" onClick={() => submit('BET')}>
            <small>BET</small>벳 <b>{clamped.toLocaleString()}</b>
          </button>
        )}
        {canRaise && (
          <button className="act raise" onClick={() => submit('RAISE')}>
            <small>RAISE TO</small>레이즈 <b>{clamped.toLocaleString()}</b>
          </button>
        )}
      </div>

      {(canBet || canRaise) && (
        <div className="bet-controls">
          <div className="bet-quick">
            <button onClick={() => setAmount(String(minTo))}>최소</button>
            <button onClick={() => quick(0.5)}>½ 팟</button>
            <button onClick={() => quick(2 / 3)}>⅔ 팟</button>
            <button onClick={() => quick(1)}>팟</button>
            <button onClick={() => setAmount(String(maxTo))}>올인</button>
          </div>
          <div className="bet-slider-row">
            <input type="range" min={minTo} max={maxTo} value={clamped}
              style={{ '--fill': `${fillPct}%` }}
              onChange={(e) => setAmount(e.target.value)} />
            <input className="bet-amount-input" type="number" value={amount} placeholder={String(minTo)}
              min={minTo} max={maxTo} onChange={(e) => setAmount(e.target.value)} />
          </div>
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ AI 판단 로그 */
const ACTION_KO = { FOLD: '폴드', CHECK: '체크', CALL: '콜', BET: '벳', RAISE: '레이즈' };

function BotReasonPanel({ god }) {
  const [rows, setRows] = useState([]);
  useEffect(() => {
    const load = () => fetch(`/api/tables/t1/bots/reasons?god=${god}`)
      .then((r) => r.json()).then(setRows).catch(() => {});
    load();
    const t = setInterval(load, 2000);
    return () => clearInterval(t);
  }, [god]);
  const last = rows.slice(-12); // 최근 12개만(시간순)
  return (
    <div className="bot-reasons">
      {last.length === 0 && (
        <div className="muted sm-note">아직 공개된 AI 판단이 없습니다 — 진행 중 핸드의 판단은
          종료 후(또는 👁 관전 중) 공개됩니다.</div>
      )}
      {last.map((a, i) => (
        <div key={i} className="bot-reason-row">
          <span className="br-meta">#{a.handNo} {translateStreet(a.street)}</span>
          <b>🤖 {a.name}</b>
          <span className="br-act">{ACTION_KO[a.action] || a.action}{a.amount > 0 ? ` ${a.amount}` : ''}</span>
          <span className="br-why">{a.reason}</span>
        </div>
      ))}
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
      <div className="panel-title">ROI 리더보드</div>
      <table>
        <thead>
          <tr><th>#</th><th>플레이어</th><th>net</th><th>핸드</th><th>승</th><th>VPIP</th><th>PFR</th></tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r.playerId}>
              <td className={i === 0 ? 'rank-1' : ''}>{i + 1}</td>
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
          <div className="panel-title">세션 누적 리포트</div>
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
function PlayerAdder({ onAdd, seatedIds, saved, onForget }) {
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
    <div className="add-inline">
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

/* ------------------------------------------------------------------ 홈(랜딩) */
const FEATURES = [
  { icon: '📈', title: '실시간 이퀴티', desc: '내 홀카드 기준 몬테카를로 승률을 매 스트리트 실시간으로 표시합니다.' },
  { icon: '🧠', title: 'EV 손실 복기', desc: '핸드가 끝나면 콜·폴드 실수를 자동 감지해 EV 손실을 bb 단위로 수치화합니다.' },
  { icon: '🤖', title: 'AI 상대', desc: '프리플랍 차트와 이퀴티 vs 팟오즈로 판단하는 봇과 언제든 실전처럼 연습하세요.' },
  { icon: '🔒', title: '검증 가능한 셔플', desc: '딜 전 SHA-256 커밋을 공개하고, 종료 후 브라우저가 직접 재계산해 검증합니다.' },
  { icon: '⏱️', title: '타임뱅크', desc: '액션 30초 제한. 초과하면 자동 체크/폴드로 게임이 멈추지 않습니다.' },
  { icon: '📊', title: 'ROI 리더보드', desc: 'VPIP·PFR·순수익을 집계해 세션이 끝나도 남는 전적을 만듭니다.' },
];

function HomePage({ onAdd, seatedIds, saved, onForget, onEnter, playerCount }) {
  const [lobby, setLobby] = useState([]);
  useEffect(() => {
    const load = () => fetch('/api/tables').then((r) => r.json()).then(setLobby).catch(() => {});
    load();
    const t = setInterval(load, 4000);
    return () => clearInterval(t);
  }, []);
  return (
    <div className="login home">
      <div className="hero-logo">
        <span className="hero-mark">♠</span>
        <h1>홈포커</h1>
      </div>
      <p className="tagline">
        친구들과 즐기는 실시간 <b>노리밋 홀덤</b> — 이퀴티·복기·AI 상대까지 붙은
        학습용 포커 테이블입니다.
      </p>

      <div className="home-features">
        {FEATURES.map((f) => (
          <div key={f.title} className="feature-card">
            <span className="fc-icon">{f.icon}</span>
            <b>{f.title}</b>
            <p>{f.desc}</p>
          </div>
        ))}
      </div>

      <div className="home-panel">
        <div className="hp-title">게임 참가</div>
        <div className="hp-sub">플레이어를 착석시키면 자동으로 테이블에 입장합니다 (2명 이상이면 시작 가능).</div>
        <PlayerAdder onAdd={onAdd} seatedIds={seatedIds} saved={saved} onForget={onForget} />
        {playerCount > 0 && (
          <button className="primary enter-btn" onClick={onEnter}>
            🎲 테이블 입장 — {playerCount}명 착석 중
          </button>
        )}
      </div>

      {lobby.length > 0 && (
        <div className="home-lobby">
          <div className="panel-title hp-title">테이블 로비</div>
          <table>
            <thead>
              <tr><th>테이블</th><th>게임</th><th>블라인드</th><th>착석</th><th>상태</th><th>진행 핸드</th></tr>
            </thead>
            <tbody>
              {lobby.map((t) => (
                <tr key={t.tableId}>
                  <td>{t.tableId}</td>
                  <td>홀덤 · 노리밋</td>
                  <td>{t.smallBlind}/{t.bigBlind}</td>
                  <td>{t.seatedCount}명</td>
                  <td>{t.handInProgress ? <span className="live">● 진행 중</span> : '대기'}</td>
                  <td>{t.handsPlayed}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="home-footer">
        ♠ 홈포커 — 학습용 홀덤 클라이언트 · 실제 돈이 오가지 않습니다 · 커밋-리빌 셔플 검증 지원
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ 앱 */
export default function App() {
  const { players, views, errors, connected, addPlayer, removePlayer, startHand, act } = usePokerTable();
  const [picked, setPicked] = useState(null);
  const [saved, setSaved] = useState(loadSaved);
  const [screen, setScreen] = useState('home'); // 'home' | 'table'
  const [showReplay, setShowReplay] = useState(false);
  const [commitment, setCommitment] = useState(null);
  const [godMode, setGodMode] = useState(false);
  const [godSeats, setGodSeats] = useState(null); // playerId -> holeCards (전지적 뷰)
  const [showBotLog, setShowBotLog] = useState(false);
  const [blinds, setBlinds] = useState(null); // {smallBlind, bigBlind}

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

  // 헤더에 표시할 블라인드 정보(로비 API에서 1회 로드).
  useEffect(() => {
    fetch('/api/tables').then((r) => r.json())
      .then((list) => setBlinds(list.find((t) => t.tableId === 't1') || list[0] || null))
      .catch(() => {});
  }, []);

  // 핸드 시작/종료 시 현재 셔플 커밋 해시를 갱신(시작 전 공개 = 조작 불가 증명의 앞단).
  const inProgress = state?.handInProgress ?? false;
  useEffect(() => {
    if (!state) { setCommitment(null); return; }
    fetch('/api/tables/t1/fairness').then((r) => r.json())
      .then((f) => setCommitment(f.currentCommitment)).catch(() => {});
  }, [inProgress]);

  // 전지적 관찰자 시점: 내가 플레이 중이 아닐 때(폴드/미착석/핸드 종료)만 버튼으로 켠다.
  const mySeat = state?.seats?.find((s) => s.playerId === activeId);
  const canGodView = !!state && (!inProgress || !mySeat || mySeat.status === 'FOLDED');
  useEffect(() => {
    if (!godMode || !canGodView) { setGodSeats(null); return undefined; }
    let live = true;
    const load = () => fetch('/api/tables/t1/godview').then((r) => r.json())
      .then((v) => { if (live) setGodSeats(Object.fromEntries(v.seats.map((s) => [s.playerId, s.holeCards]))); })
      .catch(() => {});
    load();
    const t = setInterval(load, 1200); // 남은 판의 진행(새 카드 딜)을 따라간다
    return () => { live = false; clearInterval(t); };
  }, [godMode, canGodView, state?.street, state?.currentActorId]);

  const addBot = () => fetch('/api/tables/t1/bots', { method: 'POST' }).catch(() => {});
  const removeBot = () => fetch('/api/tables/t1/bots', { method: 'DELETE' }).catch(() => {});
  const hasBots = state?.seats?.some((s) => s.playerId.startsWith('ai-'));

  // 자동 다음 핸드(오토딜) — 서버 설정을 읽어오고, 버튼으로 토글한다.
  const [autoDeal, setAutoDeal] = useState(true);
  useEffect(() => {
    fetch('/api/tables/t1/autodeal').then((r) => r.json())
      .then((d) => setAutoDeal(d.enabled)).catch(() => {});
  }, []);
  const toggleAutoDeal = () => {
    const next = !autoDeal;
    setAutoDeal(next); // 낙관적 반영
    fetch('/api/tables/t1/autodeal', {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled: next }),
    }).then((r) => r.json()).then((d) => setAutoDeal(d.enabled)).catch(() => {});
  };

  // 홈(랜딩) 화면 — 착석자가 아직 없으면 테이블 화면 대신 항상 홈을 보여준다.
  if (screen === 'home' || players.length === 0) {
    return (
      <HomePage
        onAdd={(id, name) => { addAndSave(id, name); setScreen('table'); }}
        seatedIds={seatedIds} saved={saved} onForget={forget}
        onEnter={() => setScreen('table')} playerCount={players.length} />
    );
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
        <span className="brand">
          <span className="brand-mark">♠</span>
          <span className="brand-word"><b>홈포커</b><span>Hold'em</span></span>
        </span>
        <span className="table-meta">
          <b>텍사스 홀덤 · 노리밋</b>
          <span>{blinds ? `블라인드 ${blinds.smallBlind}/${blinds.bigBlind} · ` : ''}테이블 t1</span>
        </span>
        <span className="whoami">
          <Avatar id={activeId} name={activeId} mini />
          <b>{activeId}</b>
          <span className={`dot ${connected[activeId] ? 'on' : 'off'}`} />
        </span>
        <span className="hbtns">
          <button className="ghost" onClick={() => setScreen('home')}
            title="홈 화면으로(연결·게임 상태는 유지됩니다)">🏠 홈</button>
          {canGodView && (
            <button className={`ghost ${godMode ? 'god-on' : ''}`} onClick={() => setGodMode((v) => !v)}
              title="내가 플레이 중이 아닐 때, 남은 판을 전지적 시점으로 관찰">
              {godMode ? '👁 패 숨기기' : '👁 상대 패 보기'}
            </button>
          )}
          <button className="ghost" onClick={() => setShowReplay((v) => !v)}>
            {showReplay ? '복기 닫기' : '핸드 복기'}
          </button>
          <button className={`ghost ${autoDeal ? 'god-on' : ''}`} onClick={toggleAutoDeal}
            title="핸드가 끝나면 잠시 후 자동으로 다음 핸드를 시작합니다(칩 보유 2명 미만이면 중단)">
            {autoDeal ? '▶ 자동진행 ON' : '⏸ 자동진행 OFF'}
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
              <Avatar id={p.id} name={p.name} mini />
              {p.name}
              <span className={`dot ${connected[p.id] ? 'on' : 'off'}`} />
              {p.id === actorId && <span className="turn-badge">차례</span>}
              <button className="x" title="퇴장"
                onClick={(e) => { e.stopPropagation(); removePlayer(p.id); }}>×</button>
            </span>
          ))}
        </div>
        <PlayerAdder onAdd={addAndSave} seatedIds={seatedIds} saved={saved} onForget={forget} />
        <div className="bot-controls">
          <button className="ghost sm" onClick={addBot}
            title="서버가 알아서 플레이하는 AI 상대를 앉힙니다(이퀴티 vs 팟오즈 기반)">🤖 AI 상대 추가</button>
          {hasBots && !inProgress && (
            <button className="ghost sm" onClick={removeBot}>AI 제거</button>
          )}
          {hasBots && (
            <button className={`ghost sm ${showBotLog ? 'god-on' : ''}`}
              onClick={() => setShowBotLog((v) => !v)}
              title="AI 가 왜 그렇게 행동했는지(이퀴티 vs 팟오즈 근거) 봅니다. 진행 중 핸드는 종료 후 공개">
              🧠 AI 판단 로그
            </button>
          )}
          {hasBots && <span className="muted sm-note">AI 는 자기 차례에 자동으로 액션합니다</span>}
        </div>
        {hasBots && showBotLog && <BotReasonPanel god={godMode && canGodView} />}
      </div>

      {error && <div className="error">⚠ {error}</div>}
      {!state && <div className="muted center">연결 중…</div>}

      {state && (
        <main>
          <div className="table-wrap">
            <div className="poker-table">
              <div className="rail" />
              <div className="felt">
                <div className="felt-brand">
                  <span className="fb-suit">♠</span>
                  <span className="fb-word">HOME POKER</span>
                </div>
                <div className="table-center">
                  <div className="street-badge">{translateStreet(state.street)}</div>
                  <div className="board">
                    {state.board.length === 0
                      ? <span className="board-empty">— 보드 —</span>
                      : state.board.map((c, i) => <Card key={c} code={c} flip delay={i * 120} />)}
                  </div>
                  <div className="pot">
                    <ChipStack amount={state.pot} cap={4} />
                    <span className="pot-label">POT</span><b>{state.pot.toLocaleString()}</b>
                  </div>
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

                {ordered.map((s, i) => {
                  // 전지적 시점이 켜져 있으면 리댁션된 좌석에 서버 godview 의 홀카드를 덧입힌다
                  const spied = godMode && !s.holeCards && !!godSeats?.[s.playerId];
                  const seat = spied ? { ...s, holeCards: godSeats[s.playerId] } : s;
                  return (
                    <Seat key={s.playerId} seat={seat} pos={positions[i]}
                      isViewer={s.playerId === activeId} spied={spied}
                      secondsLeft={state.turnSecondsLeft} actorId={actorId}
                      isWinner={done && payouts[s.playerId] > 0}
                      winAmount={payouts[s.playerId] || 0} />
                  );
                })}
              </div>
            </div>
          </div>

          {state.handInProgress && (
            actorId === activeId
              ? <ActionBar state={state} mySeat={mySeat}
                  act={(type, amount) => act(activeId, type, amount)} />
              : <div className="turn-hint">
                  <TimerRing actorId={actorId} seconds={state.turnSecondsLeft} />
                  {actorId?.startsWith('ai-')
                    ? <>🤖 <b>{actorId}</b> (AI)가 생각 중…</>
                    : <>지금은 <b>{actorId}</b> 차례
                        {actorId && <button className="ghost sm" onClick={() => setPicked(actorId)}>{actorId}(으)로 전환 →</button>}
                      </>}
                </div>
          )}

          {done && Object.keys(payouts).length > 0 && (
            <div className="result">
              🏆 {Object.entries(payouts).filter(([, a]) => a > 0)
                .map(([id, amt]) => <span key={id} className="win-name">{id} +{amt} </span>)}
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
