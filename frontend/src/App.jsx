import React, { useEffect, useRef, useState } from 'react';
import { usePokerTable } from './usePokerTable.js';

const SUIT = { s: '♠', h: '♥', d: '♦', c: '♣' };
const prettyCard = (c) => (c ? c.slice(0, -1) + (SUIT[c.slice(-1)] || c.slice(-1)) : '');

/* ------------------------------------------------------------------ 초보자용 용어 사전
   복기·AI 판단 로그·리더보드에 나오는 포커 용어를 클릭하면 설명이 펼쳐진다. */
const GLOSSARY = {
  '필요이퀴티': '콜이 손해가 아니려면 필요한 최소 승률. 콜 금액 ÷ (팟 + 콜 금액)으로 계산해요. 예: 팟 100에 콜 50 → 50÷150 = 33%. 승률이 이보다 높으면 콜이 이득입니다.',
  '이퀴티': '지금 카드로 끝까지 갔을 때 이길 확률(승률). 상대 카드를 모르기 때문에 무작위로 수천 번 시뮬레이션(몬테카를로)해 평균낸 값이에요.',
  '팟오즈': '팟 크기 대비 콜 금액의 배당. "필요이퀴티"와 같은 개념의 다른 표현 — 배당이 좋을수록 낮은 승률로도 콜할 수 있어요.',
  '레이즈마진': '필요 승률을 이만큼(%p) 크게 넘을 때만 레이즈한다는 봇의 여유 기준. 아슬아슬한 우위로는 콜만 해요.',
  '마진': '필요 승률보다 조금 낮다고 바로 접지 않도록 두는 여유 폭 — 시뮬레이션 오차를 감안한 안전장치예요.',
  '밸류벳': '내가 이기고 있다고 볼 때, 나보다 약한 핸드의 콜을 받아 이득을 보려는 베팅.',
  '씬 밸류': '아슬아슬하게 이기고 있는 핸드로 하는 얇은 밸류벳. 겁먹고 체크하면 그만큼 이득을 놓쳐요.',
  'C-벳': '컨티뉴에이션 벳 — 프리플랍에서 레이즈한 사람이 플랍에서도 이어가는 베팅. 상대가 플랍을 못 맞췄을 확률(약 2/3)을 노려요.',
  '세미블러프': '지금은 지고 있지만 완성되면 이기는 드로우로 하는 베팅. 상대가 접어도 이득, 카드가 떠도 이득 — 이기는 길이 2개예요.',
  '블러프': '이길 수 없는 핸드로 상대를 폴드시키려는 베팅.',
  '콤보 드로우': '플러쉬드로우+스트레이트드로우처럼 드로우가 겹친 핸드. 아웃이 12개 이상이라 톱페어 상대로도 거의 반반이에요.',
  '드로우': '아직 미완성이지만 특정 카드가 나오면 강해지는 핸드(플러쉬드로우, 스트레이트드로우 등).',
  '아웃': '내 핸드를 완성시켜 주는 남은 카드 수. 아웃 1개 ≈ 다음 카드 1장당 약 2% 승률(9아웃 플러쉬드로우 ≈ 19%).',
  '에어': '페어도 드로우도 없는, 아무것도 못 맞춘 핸드.',
  '미디엄': '톱페어에 못 미치는 애매한 페어. 목표는 팟을 키우지 않고 싸게 쇼다운까지 가는 것.',
  '오버페어': '보드의 가장 높은 카드보다 큰 포켓페어(예: 보드 K72에 내가 AA).',
  '톱페어': '보드의 가장 높은 카드와 짝을 이룬 페어(예: 보드 K72에 내가 KQ).',
  '원페어': '페어 하나뿐인 핸드. 작은 팟용 핸드라 팟이 커질수록 지고 있을 확률이 높아요.',
  '셋/투페어': '셋 = 포켓페어가 보드와 만나 만든 숨은 트리플, 투페어 = 홀카드 두 장이 모두 보드와 짝. 둘 다 팟을 키울 만한 강한 핸드예요.',
  '괴물': '스트레이트 이상(스트레이트·플러쉬·풀하우스…)의 최강급 핸드. 과제는 상대에게서 최대한 많은 칩을 뽑는 것.',
  '쇼다운': '베팅이 모두 끝난 뒤 카드를 공개해 승자를 가리는 것.',
  '팟 컨트롤': '애매한 핸드로는 팟이 커지지 않게 베팅을 줄이거나 체크하는 것 — "큰 팟엔 큰 핸드"가 원칙이에요.',
  '콜스테이션': '어떤 베팅에도 잘 접지 않는 상대. 블러프가 안 통하니 좋은 핸드로 밸류벳만 해야 해요.',
  '니트': '아주 좋은 핸드만 플레이하는 극단적으로 타이트한 상대. 이런 상대의 베팅·레이즈는 진짜예요.',
  'LAG': '루즈-어그레시브 — 많은 핸드로 공격적으로 치는 상대. 베팅을 다 믿을 필요는 없지만, 강한 저항엔 물러나요.',
  '3벳': '오픈 레이즈에 대한 재레이즈(블라인드가 1번째, 오픈이 2번째 베팅이라 "3벳"). 강한 핸드의 신호예요.',
  '4벳': '3벳에 대한 재재레이즈. 보통 최상위 핸드(AA/KK급)의 신호.',
  '5벳': '4벳에 대한 재레이즈 — 100bb 게임에선 사실상 올인이에요.',
  '스퀴즈': '오픈 레이즈에 콜러까지 있을 때 크게 재레이즈해 양쪽을 동시에 압박하는 플레이.',
  '레인지': '그 상황에서 상대가 들 수 있는 모든 핸드의 집합. 한 핸드를 콕 집는 게 아니라 범위로 생각해요.',
  '스틸': '모두가 약해 보일 때 팟을 훔치려는 베팅.',
  '스케어 보드': '드로우가 완성됐을 법한 무서운 보드(같은 무늬 3장, 스트레이트 연결 완성 등).',
  '마른 보드': '드로우가 없는 흩어진 보드(예: Q72 무늬 제각각). 아무도 못 맞췄을 확률이 높아 블러프(C-벳)가 잘 통해요.',
  '젖은 보드': '연결된 숫자·같은 무늬가 깔려 드로우가 많은 보드(예: J T 9 투톤). 상대가 따라올 이유가 많아요.',
  '페어드 보드': '같은 숫자 페어가 깔린 보드(예: 8 8 2). 서로 맞추기 어려워 마른 보드처럼 취급해요.',
  'A하이 보드': '가장 높은 카드가 A인 보드. 프리플랍 레이저(A를 많이 듦)에게 유리한 보드예요.',
  '몬테카를로': '무작위 시뮬레이션을 수천 번 반복해 평균으로 확률을 구하는 방법.',
  'GTO': '게임이론 최적 전략(Game Theory Optimal). 여기 판정은 GTO가 아니라 단순한 "승률 vs 배당" 비교예요.',
  '트랩': '강한 핸드를 약한 척 체크/콜만 하며 상대의 베팅을 유도하는 것.',
  '콜다운': '레이즈 없이 리버까지 콜로만 따라가는 것.',
  '오버콜': '이길 확률이 필요이퀴티에 못 미치는데 콜한 실수 — 배당보다 비싸게 산 콜.',
  '오버폴드': '이길 확률이 충분한데 폴드한 실수 — 장기적으로 이득인 콜을 버린 셈이에요.',
  '판정 지점': '이퀴티 계산이 가능한 콜/폴드 결정 지점의 수. 이 지점들만 실수 여부를 판정해요.',
  'VPIP': '자발적으로 팟에 돈을 넣은 핸드 비율(%). 6인 기준 15~30이 보통 — 높을수록 아무 핸드나 치는 루즈한 스타일.',
  'PFR': '프리플랍에서 레이즈한 핸드 비율(%). VPIP와 가까울수록 공격적인 스타일이에요.',
  'net': '누적 순수익 — 딴 칩에서 잃은 칩을 뺀 값.',
  'EV': '기대값(Expected Value) — 같은 결정을 무수히 반복했을 때의 평균 손익.',
  'bb': '빅블라인드 단위. -2bb = 빅블라인드 2개만큼 손해. 판돈 크기와 무관하게 비교할 수 있는 단위예요.',
  '차트': '프리플랍 전략표 — 포지션·상황별로 어떤 핸드를 레이즈/콜/폴드할지 정리한 표(봇은 BTS 교재 차트를 따라요).',
  '해링턴': '댄 해링턴의 캐시게임 교재. 봇의 포스트플랍(플랍 이후) 전략 출처예요.',
  'UTG': '가장 먼저 액션하는 자리(언더 더 건) — 가장 불리해서 좋은 핸드만 쳐요.',
  'BTN': '딜러 버튼 자리 — 항상 마지막에 액션하는 가장 유리한 자리.',
  'CO': '버튼 바로 앞자리(컷오프). 후반 포지션이라 오픈 범위가 넓어요.',
  'MP': '중간 포지션(미들 포지션).',
  'SB': '스몰 블라인드 자리 — 포스트플랍에서 가장 먼저 액션해야 해서 불리해요.',
  'BB': '빅 블라인드 자리. 이미 돈을 냈으니 좋은 배당으로 방어(콜)할 수 있어요.',
};

const TERM_RE = new RegExp(
  `(${Object.keys(GLOSSARY)
    .sort((a, b) => b.length - a.length)
    .map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
    .join('|')})`,
  'g',
);

/** 클릭하면 바로 아래에 설명이 펼쳐지는 용어. */
function Term({ word }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" className={`term ${open ? 'open' : ''}`}
        onClick={(e) => { e.stopPropagation(); setOpen((v) => !v); }}>
        {word}
      </button>
      {open && (
        <span className="term-note" onClick={(e) => e.stopPropagation()}>
          💡 <b>{word}</b> — {GLOSSARY[word]}
        </span>
      )}
    </>
  );
}

/** 문장 속의 용어(점선 밑줄)를 클릭 가능한 <Term>으로 바꿔 렌더링한다. */
function JargonText({ text }) {
  if (text == null) return null;
  return (
    <>
      {String(text).split(TERM_RE).map((p, i) => (GLOSSARY[p] ? <Term key={i} word={p} /> : p))}
    </>
  );
}

/* ------------------------------------------------------------------ 효과음 (WebAudio 합성 — 파일 불필요) */
let audioCtx = null;
function playTone(freq, dur = 0.09, type = 'sine', gain = 0.05, when = 0) {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    if (audioCtx.state === 'suspended') audioCtx.resume().catch(() => {});
    const t0 = audioCtx.currentTime + when;
    const osc = audioCtx.createOscillator();
    const g = audioCtx.createGain();
    osc.type = type;
    osc.frequency.value = freq;
    g.gain.setValueAtTime(gain, t0);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    osc.connect(g); g.connect(audioCtx.destination);
    osc.start(t0); osc.stop(t0 + dur + 0.02);
  } catch { /* 오디오 미지원/차단 시 무시 */ }
}
const SFX = {
  myTurn: () => { playTone(880, 0.09, 'sine', 0.06); playTone(1318, 0.13, 'sine', 0.05, 0.1); },
  deal: () => playTone(300, 0.05, 'triangle', 0.045),
  chip: () => { playTone(1600, 0.035, 'square', 0.02); playTone(2100, 0.03, 'square', 0.015, 0.045); },
  win: () => [523, 659, 784, 1047].forEach((f, i) => playTone(f, 0.15, 'sine', 0.05, i * 0.09)),
  // 올인 런아웃 전용: 심장 쿵쿵(턴) / 상승 리저 + 쿵(리버 직전 최대 서스펜스)
  thump: () => { playTone(76, 0.22, 'sine', 0.15); playTone(54, 0.3, 'sine', 0.12, 0.15); },
  riser: () => {
    [196, 233, 294, 370, 466].forEach((f, i) => playTone(f, 0.07, 'triangle', 0.035, i * 0.06));
    playTone(76, 0.24, 'sine', 0.16, 0.34); playTone(54, 0.32, 'sine', 0.13, 0.5);
  },
};

/* ------------------------------------------------------------------ 저장된 플레이어(localStorage) */
const SAVED_KEY = 'homepoker.players';
function loadSaved() {
  try { return JSON.parse(localStorage.getItem(SAVED_KEY)) || []; } catch { return []; }
}
function persistSaved(list) {
  try { localStorage.setItem(SAVED_KEY, JSON.stringify(list)); } catch { /* 무시 */ }
}

/* ------------------------------------------------------------------ 카드 */
function Card({ code, faceDown, delay = 0, flip, dramatic }) {
  if (faceDown || !code) {
    return <span className="card back" style={{ animationDelay: `${delay}ms` }} />;
  }
  const rank = code.slice(0, -1);
  const suit = code.slice(-1);
  const red = suit === 'h' || suit === 'd';
  const sym = SUIT[suit] || suit;
  return (
    <span className={`card ${red ? 'red' : 'black'} ${dramatic ? 'dramatic' : flip ? 'flip' : ''}`}
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

/* ------------------------------------------------------------------ 카운트다운 바(줄어드는 시간 바) */
function TimerBar({ actorId, seconds, total = 30 }) {
  const [left, setLeft] = useState(seconds ?? 0);
  useEffect(() => {
    setLeft(seconds ?? 0);
    const t = setInterval(() => setLeft((s) => (s > 0 ? s - 1 : 0)), 1000);
    return () => clearInterval(t);
  }, [actorId, seconds]);
  const frac = Math.max(0, Math.min(1, left / total));
  return (
    <span className={`timer-bar ${left <= 5 ? 'urgent' : ''}`}
      title={`남은 시간 ${left}초`}>
      <span className="tb-fill" style={{ width: `${frac * 100}%` }} />
    </span>
  );
}

/* ------------------------------------------------------------------ 좌석(테이블 둘레에 배치) */
const BUBBLE_KO = { CHECK: '체크', CALL: '콜', BET: '벳', RAISE: '레이즈', FOLD: '폴드' };
function bubbleText(lastAction) {
  const [type, amount] = lastAction.split(' ');
  return (BUBBLE_KO[type] || type) + (amount ? ` ${Number(amount).toLocaleString()}` : '');
}

function Seat({ seat, isViewer, pos, secondsLeft, actorId, isWinner, winAmount, spied }) {
  const cards = seat.holeCards
    ? seat.holeCards.map((c, i) => <Card key={i} code={c} flip delay={i * 90} />)
    : (seat.status !== 'FOLDED' ? [<Card key="a" faceDown />, <Card key="b" faceDown />] : null);
  return (
    <div className={`seat ${isViewer ? 'me' : ''} ${seat.currentActor ? 'acting' : ''} ${seat.status === 'FOLDED' ? 'folded' : ''} ${isWinner ? 'winner' : ''} ${spied ? 'spied' : ''}`}
      style={{ left: `${pos.x}%`, top: `${pos.y}%` }}>
      {seat.lastAction && seat.status !== 'FOLDED' && !seat.currentActor && (
        <div className={`action-bubble ab-${seat.lastAction.split(' ')[0].toLowerCase()}`}
          key={seat.lastAction}>
          {bubbleText(seat.lastAction)}
        </div>
      )}
      {isWinner && winAmount > 0 && <div className="win-amount">+{winAmount}</div>}
      {spied && <div className="spy-tag" title="전지적 관찰자 시점으로 공개된 카드">👁</div>}
      {seat.handLabel && seat.holeCards && seat.status !== 'FOLDED' && (
        <div className={`hand-label ${isWinner ? 'hl-winner' : ''}`} key={seat.handLabel}>
          {isWinner ? '🏆 ' : ''}{seat.handLabel}
        </div>
      )}
      <div className="seat-cards">{cards}</div>
      <div className="seat-plate">
        {seat.button && <span className="dealer" title="딜러 버튼">D</span>}
        <Avatar id={seat.playerId} name={seat.name} />
        <div className="plate-info">
          <div className="seat-name">{seat.name}{isViewer ? ' (나)' : ''}</div>
          <div className="seat-stack">{seat.stack.toLocaleString()}</div>
        </div>
      </div>
      {seat.currentActor && actorId && (
        <div className="seat-timer"><TimerBar actorId={actorId} seconds={secondsLeft} /></div>
      )}
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
            <small>CALL</small>콜 <b>{(state.viewerToCall ?? 0).toLocaleString()}</b>
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
      {last.length > 0 && (
        <div className="muted sm-note">점선 밑줄 용어(<Term word="이퀴티" /> 등)를 누르면 설명이 펼쳐집니다.</div>
      )}
      {last.map((a, i) => (
        <div key={i} className="bot-reason-row">
          <span className="br-meta">#{a.handNo} {translateStreet(a.street)}</span>
          <b>🤖 {a.name}</b>
          <span className="br-act">{ACTION_KO[a.action] || a.action}{a.amount > 0 ? ` ${a.amount}` : ''}</span>
          <span className="br-why"><JargonText text={a.reason} /></span>
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
          <tr>
            <th>#</th><th>플레이어</th>
            <th><JargonText text="net" /></th>
            <th>핸드</th><th>승</th>
            <th><JargonText text="VPIP" /></th>
            <th><JargonText text="PFR" /></th>
          </tr>
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

/** 실수 판정을 초보자 문장으로 풀어쓴다(용어는 JargonText 로 감싸 클릭 설명 제공). */
function mistakeExplain(d) {
  const eq = pct(d.equity);
  const req = pct(d.requiredEquity);
  const loss = d.evLossBb.toFixed(1);
  return d.action === 'CALL'
    ? `왜 실수인가요? 이 콜이 본전이 되려면 승률이 최소 ${req}%(필요이퀴티)는 돼야 했는데, `
      + `실제 이길 확률(이퀴티)은 ${eq}%뿐이었어요. 부족한 확률만큼 밑지는 거래라, `
      + `같은 콜을 반복하면 평균 ${loss}bb씩 잃습니다. 이런 지점은 폴드가 이득이에요.`
    : `왜 실수인가요? 폴드했지만 이길 확률(이퀴티)이 ${eq}%로, 콜 비용 대비 필요한 `
      + `${req}%(필요이퀴티)보다 높았어요. 배당이 남는 콜을 접은 셈이라 평균 ${loss}bb를 `
      + `놓쳤습니다. 이런 지점은 콜이 이득이에요.`;
}

function ReplayPanel({ onClose, viewerId }) {
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
  // 복기는 "나"의 코칭 — 실수 배너·마커는 내(viewerId) 콜/폴드만 보여준다.
  // (AI 는 자기 규칙대로 플레이하므로 AI 판정까지 띄우면 "AI가 스스로 실수 분석"처럼 보인다.)
  const myMistakes = (review?.decisions || []).filter(
    (d) => d.mistake && (!viewerId || d.playerId === viewerId),
  );
  // 액션 인덱스 d.step 의 결과가 반영된 프레임은 step+1 → 그 프레임에 실수 마커를 찍는다.
  const mistakeAt = {};
  myMistakes.forEach((d) => { mistakeAt[d.step + 1] = d; });
  const worst = myMistakes.reduce((a, b) => (!a || b.evLossBb > a.evLossBb ? b : a), null);
  const curMistake = mistakeAt[step];

  return (
    <div className="replay-panel">
      <div className="replay-head">
        <b>핸드 복기</b>
        <span className="muted">
          <JargonText text="내 콜/폴드를 이퀴티 vs 팟오즈로 판정해 EV 손실 실수 감지" /> · 점선 용어를 누르면 설명
        </span>
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
            <>
              <div className="mistake-banner worst" onClick={() => setStep(worst.step + 1)}
                title="클릭하면 그 지점으로 이동">
                ⚠ 최대 실수: <b>{worst.playerName}</b> {translateStreet(worst.street)}{' '}
                {worst.action === 'CALL' ? '콜' : '폴드'} —{' '}
                <JargonText text={`이퀴티 ${pct(worst.equity)}% vs 필요이퀴티 ${pct(worst.requiredEquity)}%`} />{' '}
                (<b>-{worst.evLossBb.toFixed(1)}bb</b>)
              </div>
              <div className="mistake-explain"><JargonText text={mistakeExplain(worst)} /></div>
            </>
          )}
          {review && !worst && <div className="mistake-banner clean">✔ 이 핸드에서 감지된 내 실수 없음</div>}

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
                  {s.handLabel && s.status !== 'FOLDED' && <span className="r-hand-label">{s.handLabel}</span>}
                  <span className="rstack"><span className="chip-ico sm" />{s.stack}</span>
                  {s.committedThisStreet > 0 && <span className="rbet">벳 {s.committedThisStreet}</span>}
                  {s.status === 'FOLDED' && <span className="fold-tag">FOLD</span>}
                </div>
              ))}
            </div>
            <div className="replay-action">{frame.action ? `▶ ${frame.action}` : '핸드 시작(딜·블라인드 직후)'}</div>
            {curMistake && (
              <>
                <div className="mistake-banner">
                  ⚠ <b>{curMistake.playerName}</b>의 {curMistake.action === 'CALL' ? '콜' : '폴드'}:{' '}
                  <JargonText text={`이퀴티 ${pct(curMistake.equity)}%, 필요이퀴티 ${pct(curMistake.requiredEquity)}% → EV`} />{' '}
                  <b>-{curMistake.evLossBb.toFixed(1)}bb</b>
                </div>
                <div className="mistake-explain"><JargonText text={mistakeExplain(curMistake)} /></div>
              </>
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
          {review && <div className="assumption"><JargonText text={review.assumption} /></div>}
        </div>
      )}

      {session.length > 0 && (
        <div className="session-report">
          <div className="panel-title">세션 누적 리포트</div>
          <table>
            <thead>
              <tr>
                <th>플레이어</th>
                <th><JargonText text="판정 지점" /></th>
                <th>실수</th>
                <th><JargonText text="EV" /> 손실 합</th>
                <th>최다 유형</th>
              </tr>
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
                  <td>{r.topMistakeType ? <JargonText text={translateMistakeType(r.topMistakeType)} /> : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ 내 승률 배지(클릭 → 계산 근거 설명) */
function EquityBadge({ state, mySeat }) {
  const [open, setOpen] = useState(false);
  if (state.viewerEquity == null) return null;
  const eq = Math.round(state.viewerEquity * 100);
  const opps = state.seats.filter(
    (s) => s.playerId !== mySeat?.playerId && s.status !== 'FOLDED',
  ).length;
  const myCards = (mySeat?.holeCards || []).map(prettyCard).join(' ');
  const boardTxt = state.board.length > 0 ? state.board.map(prettyCard).join(' ') : '아직 없음(프리플랍)';
  return (
    <div className="equity-wrap">
      <button type="button" className="equity" onClick={() => setOpen((v) => !v)}
        title="클릭하면 이 숫자가 어떻게 계산됐는지 설명이 나옵니다">
        내 승률 {eq}% <span className="eq-q">?</span>
      </button>
      {open && (
        <div className="equity-pop">
          <b>이 승률은 어떻게 나온 숫자인가요?</b>
          <p>
            내 카드 <b>{myCards || '?'}</b> 와 보드(<b>{boardTxt}</b>)는 그대로 두고,
            상대 <b>{opps}명</b>의 홀카드와 아직 안 나온 보드 카드를 남은 덱에서
            <b> 무작위로 수천 번</b> 나눠 끝까지 돌려본 시뮬레이션(몬테카를로) 결과예요.
            그중 약 <b>{eq}%</b>를 이겼다는 뜻입니다(무승부는 절반으로 계산).
          </p>
          <p className="muted">
            상대의 실제 카드를 모르니 "상대는 아무 두 장이나 들 수 있다"고 가정한 값이에요.
            상대가 레이즈로 강함을 보였다면 실제 승률은 이보다 낮을 수 있고,
            숫자가 매번 조금씩 흔들리는 것도 매번 새로 시뮬레이션하기 때문입니다.
          </p>
          <button type="button" className="ghost sm" onClick={() => setOpen(false)}>닫기 ×</button>
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

/* ------------------------------------------------------------------ 올인 런아웃 연출 */
/**
 * 올인 등으로 남은 보드(플랍·턴·리버)가 한 프레임에 전부 깔리며 끝난 핸드를,
 * "홀카드 공개 → 플랍 → 턴 → 리버 → 결과" 순서로 시차 공개해 실제 온라인 포커처럼
 * 긴장감을 만든다. 서버 상태는 그대로 두고 화면 표시만 지연하는 순수 프론트 연출 —
 * 연출 프레임 동안은 스택·팟 분배·승자 표시를 결과 공개 시점까지 보류한다.
 * (자동진행이 연출을 자르지 않도록 서버 쪽 쇼다운 추가 대기와 짝을 이룬다.)
 */
function useAllInRunout(live) {
  const [staged, setStaged] = useState(null); // { frame, phase: 'hole'|'flop'|'turn'|'river' }
  const prevRef = useRef(null);
  const timersRef = useRef([]);

  useEffect(() => {
    const prev = prevRef.current;
    prevRef.current = live;
    if (!live) return;
    if (live.handInProgress) {
      // 새 핸드/새 액션이 오면 잔여 연출 정리(자동진행 대기가 연출보다 길어 실제론 드묾)
      timersRef.current.forEach(clearTimeout);
      timersRef.current = [];
      setStaged(null);
      return;
    }
    if (!prev || !prev.handInProgress) return; // 종료 상태의 재수신 등은 무시
    const from = prev.board?.length ?? 0;
    const to = live.board?.length ?? 0;
    // 쇼다운 공개(가려져 있던 상대 카드가 열림)가 있어야 올인 런아웃 — 폴드 종료는 즉시 표시
    const revealed = live.seats.some((s) => {
      const before = prev.seats.find((p) => p.playerId === s.playerId);
      return s.holeCards && before && !before.holeCards && s.status !== 'FOLDED';
    });
    if (!revealed || to < 5 || to - from < 1) return;

    const oldStacks = Object.fromEntries(prev.seats.map((s) => [s.playerId, s.stack]));
    const frameAt = (boardLen, phase) => ({
      phase,
      frame: {
        ...live,
        handInProgress: true, // 승자·팟 분배·"새 핸드" UI 를 결과 프레임까지 보류
        payouts: {},
        board: live.board.slice(0, boardLen),
        street: boardLen === 0 ? 'PREFLOP' : boardLen === 3 ? 'FLOP' : boardLen === 4 ? 'TURN' : 'RIVER',
        // handLabel 은 최종 보드 기준이라 런아웃 중엔 숨김(리버 전에 "플러시" 스포일러 방지)
        seats: live.seats.map((s) => ({ ...s, stack: oldStacks[s.playerId] ?? s.stack, lastAction: null, handLabel: null })),
        currentActorId: null,
        turnSecondsLeft: 0,
      },
    });
    const schedule = [[0, frameAt(from, 'hole')]]; // 즉시: 홀카드 공개(올인 쇼다운), 보드는 아직 그대로
    let t = 1200;
    if (from < 3) { schedule.push([t, frameAt(3, 'flop')]); t += 1600; } // 플랍
    if (from < 4) { schedule.push([t, frameAt(4, 'turn')]); t += 1900; } // 턴(뜸 들이기)
    schedule.push([t, frameAt(5, 'river')]); t += 1500;                  // 리버(최대 서스펜스)
    schedule.push([t, null]);                                            // 결과 공개(승자·효과음)
    timersRef.current.forEach(clearTimeout);
    timersRef.current = schedule.map(([ms, frame]) => setTimeout(() => setStaged(frame), ms));
  }, [live]);

  useEffect(() => () => timersRef.current.forEach(clearTimeout), []);
  return { state: staged ? staged.frame : live, runout: staged ? { phase: staged.phase } : null };
}

/* ------------------------------------------------------------------ 앱 */
export default function App() {
  const { players, views, errors, connected, addPlayer, removePlayer, startHand, act, rebuy } = usePokerTable();
  const [picked, setPicked] = useState(null);
  const [saved, setSaved] = useState(loadSaved);
  const [screen, setScreen] = useState('home'); // 'home' | 'table'
  const [showReplay, setShowReplay] = useState(false);
  const [commitment, setCommitment] = useState(null);
  const [godMode, setGodMode] = useState(false);
  const [godSeats, setGodSeats] = useState(null); // playerId -> holeCards (전지적 뷰)
  const [showBotLog, setShowBotLog] = useState(false);
  const [blinds, setBlinds] = useState(null); // {smallBlind, bigBlind}
  const [sound, setSound] = useState(() => {
    try { return localStorage.getItem('homepoker.sound') !== 'off'; } catch { return true; }
  });
  const toggleSound = () => setSound((v) => {
    const n = !v;
    try { localStorage.setItem('homepoker.sound', n ? 'on' : 'off'); } catch { /* 무시 */ }
    return n;
  });

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
  const liveState = activeId ? views[activeId] : null;
  const { state, runout } = useAllInRunout(liveState); // 올인 런아웃은 카드를 한 장씩 시차 공개
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

  // 게임 이벤트 효과음: 내 차례 알림 / 새 보드 카드 딜 / 내 승리 팡파르.
  const sfxPrev = useRef({ actor: null, boardLen: 0, done: false });
  const boardLen = state?.board?.length ?? 0;
  useEffect(() => {
    const prev = sfxPrev.current;
    if (sound && state) {
      if (inProgress && actorId === activeId && prev.actor !== activeId) SFX.myTurn();
      if (boardLen > prev.boardLen && boardLen > 0) SFX.deal();
      if (done && !prev.done && (payouts[activeId] || 0) > 0) SFX.win();
    }
    sfxPrev.current = { actor: actorId, boardLen, done: !!done };
  }, [actorId, boardLen, done]); // eslint-disable-line react-hooks/exhaustive-deps

  // 올인 런아웃 연출: 스트리트 공개 순간 테이블 퀘이크(흔들림) + 전용 효과음.
  const [quake, setQuake] = useState(false);
  const runoutPhase = runout?.phase || null;
  useEffect(() => {
    if (!runoutPhase || runoutPhase === 'hole') return undefined;
    setQuake(true);
    if (sound) {
      if (runoutPhase === 'river') SFX.riser();
      else SFX.thump();
    }
    const t = setTimeout(() => setQuake(false), 650);
    return () => clearTimeout(t);
  }, [runoutPhase]); // eslint-disable-line react-hooks/exhaustive-deps

  // 리바인: 버스트(핸드 종료 + 내 스택 0)면 즉시 다시 살 수 있다(쿨다운 면제, 서버가 한도 관리).
  const [rebuyAmt, setRebuyAmt] = useState('');
  const [rebuyPending, setRebuyPending] = useState(false);
  const busted = !!state && !state.handInProgress && !!mySeat && mySeat.stack === 0;
  useEffect(() => {
    if (inProgress || (mySeat && mySeat.stack > 0)) setRebuyPending(false);
  }, [inProgress, mySeat?.stack]); // eslint-disable-line react-hooks/exhaustive-deps
  const doRebuy = () => {
    const amt = Number(rebuyAmt) || 1000;
    const name = players.find((p) => p.id === activeId)?.name || activeId;
    rebuy(activeId, name, amt);
    setRebuyPending(true);
  };

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
          <button className="ghost" onClick={toggleSound} title="효과음 켜기/끄기">
            {sound ? '🔊' : '🔇'}
          </button>
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

      {showReplay && <ReplayPanel viewerId={activeId} onClose={() => setShowReplay(false)} />}

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
      {!state && (
        <div className="muted center connecting"><span className="spinner" />테이블에 연결 중…</div>
      )}

      {state && (
        <main>
          <div className={`table-wrap ${quake ? 'quake' : ''}`}>
            <div className="poker-table">
              <div className="rail" />
              <div className="felt">
                {runout && <div className="runout-vignette" />}
                {runout && runout.phase !== 'hole' && (
                  <div key={runout.phase} className="reveal-flash" />
                )}
                <div className="felt-brand">
                  <span className="fb-suit">♠</span>
                  <span className="fb-word">HOME POKER</span>
                </div>
                <div className="table-center">
                  <div className={`street-badge st-${(state.street || '').toLowerCase()}`}>
                    {translateStreet(state.street)}
                  </div>
                  <div className="board">
                    {state.board.length === 0
                      ? <span className="board-empty">— 보드 —</span>
                      : state.board.map((c, i) => {
                          // 런아웃 중 방금 공개된 카드(들)는 대형 3D 플립 + 골드 글로우
                          const justRevealed = runout && runout.phase !== 'hole'
                            && (runout.phase === 'flop' ? i >= state.board.length - 3 : i === state.board.length - 1);
                          const delay = justRevealed
                            ? (runout.phase === 'flop' ? (i - (state.board.length - 3)) * 160 : 0)
                            : i * 120;
                          return <Card key={c} code={c} flip delay={delay} dramatic={justRevealed} />;
                        })}
                  </div>
                  <div className="pot">
                    <ChipStack amount={state.pot} cap={4} />
                    <span className="pot-label">POT</span><b>{state.pot.toLocaleString()}</b>
                  </div>
                  <EquityBadge state={state} mySeat={mySeat} />
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
                  act={(type, amount) => { if (sound) SFX.chip(); act(activeId, type, amount); }} />
              : actorId
                ? <div className="turn-hint">
                    <TimerBar actorId={actorId} seconds={state.turnSecondsLeft} />
                    {actorId.startsWith('ai-')
                      ? <>🤖 <b>{actorId}</b> (AI)가 생각 중…</>
                      : <>지금은 <b>{actorId}</b> 차례
                          <button className="ghost sm" onClick={() => setPicked(actorId)}>{actorId}(으)로 전환 →</button>
                        </>}
                  </div>
                : (
                  <div className="turn-hint runout">
                    <span className="runout-title">🃏 올인 쇼다운</span>
                    <span className="runout-steps">
                      {[['flop', '플랍', 3], ['turn', '턴', 4], ['river', '리버', 5]].map(([key, label, len]) => {
                        const bl = state.board.length;
                        const cls = bl >= len ? (runout?.phase === key ? 'now' : 'done') : 'wait';
                        return <span key={key} className={`rstep ${cls}`}>{label}</span>;
                      })}
                    </span>
                  </div>
                )
          )}

          {done && Object.keys(payouts).length > 0 && (
            <div className="result">
              🏆 {Object.entries(payouts).filter(([, a]) => a > 0)
                .map(([id, amt]) => <span key={id} className="win-name">{id} +{amt} </span>)}
            </div>
          )}

          {busted && (
            rebuyPending
              ? <div className="rebuy-box pending">⏳ 리바인 완료 — 다음 핸드부터 참여합니다</div>
              : (
                <div className="rebuy-box">
                  <span className="rebuy-title">💸 칩을 모두 잃었습니다</span>
                  <input type="number" inputMode="numeric" value={rebuyAmt} placeholder="1000 (400~2000)"
                    onChange={(e) => setRebuyAmt(e.target.value)} />
                  <button className="rebuy-btn" onClick={doRebuy}>
                    리바인 {(Number(rebuyAmt) || 1000).toLocaleString()}
                  </button>
                  <span className="muted sm-note">쿨다운 없이 즉시 재참여</span>
                </div>
              )
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
