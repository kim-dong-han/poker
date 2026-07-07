package com.homepoker.engine.game;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.card.Deck;
import com.homepoker.engine.eval.HandEvaluator;
import com.homepoker.engine.eval.HandRank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 노리밋 텍사스 홀덤 "한 판"의 상태머신. 순수 도메인 — WebSocket/DB/React 를 전혀 모른다.
 * 액션을 하나씩 apply() 로 먹이면 스트리트 전환·사이드팟·쇼다운까지 스스로 진행한다.
 *
 * 좌석은 생성 시의 players 순서를 시계방향으로 본다. button 은 그 리스트의 인덱스.
 *
 * 알려진 단순화(포트폴리오 범위): 풀 레이즈 미만의 올인("숏 올인")은
 * 이미 액션을 마친 플레이어에게 재레이즈 권리를 다시 열어주지 않는다(TDA 규칙과 동일).
 * 러닝잇트와이스/무크 같은 변형은 다루지 않는다.
 */
public class HandEngine {

    private final List<Player> players;
    private final int button;
    private final long smallBlind;
    private final long bigBlind;
    private final Deck deck;
    private final int n;

    private final List<Card> board = new ArrayList<>(5);
    private Street street = Street.PREFLOP;

    private final long[] committedThisStreet;
    private final long[] committedTotal;
    private final boolean[] needsToAct;

    private long currentBet;       // 이번 스트리트에 맞춰야 할 최고 커밋액
    private long lastRaiseSize;    // 최소 레이즈 증가폭 기준
    private boolean actionReopenable = true; // 직전 공격이 풀 레이즈였는가(숏 올인이면 false)
    private int actingIndex = -1;  // 현재 액션할 좌석(없으면 -1)

    private final Map<String, Long> payouts = new LinkedHashMap<>();
    private List<Pot> pots = List.of();
    private boolean showdown = false; // 쇼다운까지 갔는가(폴드 무혈입성이면 false)

    // 이벤트 소싱 기록: 초기 조건 + 적용된 액션. 리플레이(HandLog)로 상태를 결정적으로 복원한다.
    private final List<Card> initialDeckOrder;
    private final List<HandLog.Seat> initialSeats;
    private final List<Action> appliedActions = new ArrayList<>();

    public HandEngine(List<Player> players, int button, long smallBlind, long bigBlind, Deck deck) {
        this.players = List.copyOf(players);
        this.n = this.players.size();
        if (n < 2) {
            throw new IllegalArgumentException("최소 2명이 필요하다");
        }
        if (button < 0 || button >= n) {
            throw new IllegalArgumentException("버튼 인덱스 범위 오류");
        }
        if (smallBlind <= 0 || bigBlind < smallBlind) {
            throw new IllegalArgumentException("블라인드 값 오류");
        }
        this.button = button;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.deck = Objects.requireNonNull(deck, "deck");
        this.committedThisStreet = new long[n];
        this.committedTotal = new long[n];
        this.needsToAct = new boolean[n];

        // 리플레이 기록용 초기 조건 스냅샷(딜 전이므로 덱은 온전하고 스택은 시작값).
        this.initialDeckOrder = deck.remainingInOrder();
        List<HandLog.Seat> seatSnapshot = new ArrayList<>(n);
        for (Player p : this.players) {
            seatSnapshot.add(new HandLog.Seat(p.id(), p.name(), p.stack()));
        }
        this.initialSeats = List.copyOf(seatSnapshot);
    }

    // ---------------------------------------------------------------- 시작

    /** 새 핸드 시작: 홀카드 배분 + 블라인드 포스팅 + 프리플랍 첫 액션자 지정. */
    public void start() {
        for (Player p : players) {
            p.resetForNewHand();
        }
        // 홀카드 2장씩 (SB부터 시계방향, 공정성엔 무관하나 관습을 따른다)
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < n; i++) {
                players.get((sbIndex() + i) % n).dealHole(deck.deal());
            }
        }
        // 블라인드
        postBlind(sbIndex(), smallBlind);
        postBlind(bbIndex(), bigBlind);
        currentBet = bigBlind;
        lastRaiseSize = bigBlind;
        actionReopenable = true;

        // 프리플랍 액션 순서
        for (int i = 0; i < n; i++) {
            needsToAct[i] = players.get(i).status() == PlayerStatus.ACTIVE;
        }
        actingIndex = nextActionable(preflopFirst());
        // 극단적 케이스(모두 블라인드로 올인)면 바로 진행
        if (actingIndex == -1) {
            settleWhenNoMoreActions();
        }
    }

    private void postBlind(int seat, long amount) {
        long taken = players.get(seat).removeFromStack(amount);
        committedThisStreet[seat] += taken;
        committedTotal[seat] += taken;
    }

    // ---------------------------------------------------------------- 액션 적용

    public void apply(Action action) {
        if (isComplete()) {
            throw new IllegalStateException("핸드가 이미 종료됨");
        }
        if (actingIndex == -1) {
            throw new IllegalStateException("현재 액션할 플레이어가 없음");
        }
        int seat = actingIndex;
        Player actor = players.get(seat);
        if (!actor.id().equals(action.playerId())) {
            throw new IllegalStateException(
                    "당신 차례가 아님. 현재 차례: " + actor.id() + ", 요청: " + action.playerId());
        }

        long toCall = currentBet - committedThisStreet[seat];
        switch (action.type()) {
            case FOLD -> actor.fold();
            case CHECK -> {
                if (toCall != 0) {
                    throw new IllegalArgumentException("맞춰야 할 벳이 있어 체크 불가(콜/폴드/레이즈)");
                }
            }
            case CALL -> {
                if (toCall <= 0) {
                    throw new IllegalArgumentException("맞출 벳이 없음(체크를 쓰라)");
                }
                commitTo(seat, currentBet); // 스택 부족 시 자동 올인 콜
            }
            case BET -> applyBet(seat, action.amount());
            case RAISE -> applyRaise(seat, action.amount());
        }
        needsToAct[seat] = false;

        advanceAfterAction(seat);
        appliedActions.add(action); // 검증·적용에 성공한 액션만 기록
    }

    private void applyBet(int seat, long amount) {
        if (currentBet != 0) {
            throw new IllegalArgumentException("이미 벳이 있음(레이즈를 쓰라)");
        }
        Player actor = players.get(seat);
        long stack = actor.stack();
        if (amount <= 0) {
            throw new IllegalArgumentException("벳 금액은 0보다 커야 함");
        }
        if (amount > stack) {
            throw new IllegalArgumentException("보유 칩보다 큰 벳 불가");
        }
        boolean allIn = amount == stack;
        if (amount < bigBlind && !allIn) {
            throw new IllegalArgumentException("최소 벳은 빅블라인드(" + bigBlind + ")");
        }
        commitTo(seat, amount);
        currentBet = amount;
        lastRaiseSize = amount;
        actionReopenable = true;
        reopenActionForOthers(seat);
    }

    private void applyRaise(int seat, long raiseTo) {
        if (currentBet == 0) {
            throw new IllegalArgumentException("벳이 없음(벳을 쓰라)");
        }
        if (!actionReopenable) {
            throw new IllegalArgumentException("숏 올인 이후로 레이즈가 닫혔음(콜/폴드만 가능)");
        }
        Player actor = players.get(seat);
        long additional = raiseTo - committedThisStreet[seat];
        if (additional <= 0) {
            throw new IllegalArgumentException("현재 커밋보다 큰 금액으로 올려야 함");
        }
        if (additional > actor.stack()) {
            throw new IllegalArgumentException("보유 칩보다 큰 레이즈 불가");
        }
        boolean allIn = additional == actor.stack();
        long increment = raiseTo - currentBet;
        if (increment <= 0) {
            throw new IllegalArgumentException("현재 벳보다 높게 올려야 함");
        }
        long minRaiseIncrement = lastRaiseSize;
        boolean fullRaise = increment >= minRaiseIncrement;
        if (!fullRaise && !allIn) {
            throw new IllegalArgumentException("최소 레이즈 증가폭은 " + minRaiseIncrement);
        }
        commitTo(seat, raiseTo);
        currentBet = raiseTo;
        if (fullRaise) {
            lastRaiseSize = increment;
            actionReopenable = true;
            reopenActionForOthers(seat);
        } else {
            // 숏 올인: 최소 레이즈 기준은 유지하고, 이미 액션한 사람에게 재레이즈를 열지 않는다.
            actionReopenable = false;
            reopenActionForOthers(seat); // 아직 액션 안 한 사람은 여전히 콜해야 하므로 needsToAct 유지
        }
    }

    /** 이번 스트리트 커밋액을 targetTotal 까지 채운다(스택 부족 시 있는 만큼 = 올인). */
    private void commitTo(int seat, long targetTotal) {
        long desired = targetTotal - committedThisStreet[seat];
        if (desired <= 0) {
            return;
        }
        long taken = players.get(seat).removeFromStack(desired);
        committedThisStreet[seat] += taken;
        committedTotal[seat] += taken;
    }

    /** 공격(벳/레이즈)에 대응해야 하는 다른 ACTIVE 플레이어들의 액션 권리를 다시 연다. */
    private void reopenActionForOthers(int aggressorSeat) {
        for (int i = 0; i < n; i++) {
            if (i != aggressorSeat && players.get(i).status() == PlayerStatus.ACTIVE) {
                needsToAct[i] = true;
            }
        }
    }

    private void advanceAfterAction(int seat) {
        // 폴드하지 않은 플레이어가 1명뿐이면 즉시 종료
        if (countNotFolded() == 1) {
            settleUncontested();
            return;
        }
        int next = nextActionable((seat + 1) % n);
        if (next != -1) {
            actingIndex = next;
        } else {
            // 이번 베팅 라운드 종료
            advanceStreet();
        }
    }

    // ---------------------------------------------------------------- 스트리트 전환

    private void advanceStreet() {
        // 라운드 리셋
        Arrays.fill(committedThisStreet, 0);
        Arrays.fill(needsToAct, false);
        currentBet = 0;
        lastRaiseSize = bigBlind;
        actionReopenable = true;

        switch (street) {
            case PREFLOP -> {
                street = Street.FLOP;
                dealBoard(3);
            }
            case FLOP -> {
                street = Street.TURN;
                dealBoard(1);
            }
            case TURN -> {
                street = Street.RIVER;
                dealBoard(1);
            }
            case RIVER -> {
                goToShowdown();
                return;
            }
            default -> throw new IllegalStateException("전환 불가 상태: " + street);
        }

        // 액션 가능한 사람이 2명 미만이면 베팅 없이 다음 스트리트로(보드 끝까지 깐다)
        if (countCanAct() < 2) {
            advanceStreet();
            return;
        }
        for (int i = 0; i < n; i++) {
            needsToAct[i] = players.get(i).status() == PlayerStatus.ACTIVE;
        }
        actingIndex = nextActionable(postflopFirst());
    }

    private void dealBoard(int count) {
        for (int i = 0; i < count; i++) {
            board.add(deck.deal());
        }
    }

    /** 액션할 사람이 없어진 상황(전원 올인 등)에서 보드를 끝까지 깔고 정산. */
    private void settleWhenNoMoreActions() {
        while (street != Street.RIVER && board.size() < 5) {
            switch (street) {
                case PREFLOP -> {
                    street = Street.FLOP;
                    dealBoard(3);
                }
                case FLOP -> {
                    street = Street.TURN;
                    dealBoard(1);
                }
                case TURN -> {
                    street = Street.RIVER;
                    dealBoard(1);
                }
                default -> { /* no-op */ }
            }
        }
        goToShowdown();
    }

    // ---------------------------------------------------------------- 정산

    /** 폴드 안 한 사람이 1명 → 쇼다운 없이 전 팟 획득. */
    private void settleUncontested() {
        int winnerSeat = -1;
        for (int i = 0; i < n; i++) {
            if (players.get(i).status() != PlayerStatus.FOLDED) {
                winnerSeat = i;
                break;
            }
        }
        long total = Arrays.stream(committedTotal).sum();
        Player winner = players.get(winnerSeat);
        winner.addToStack(total);
        payouts.merge(winner.id(), total, Long::sum);
        pots = List.of(new Pot(total, List.of(winner.id())));
        actingIndex = -1;
        street = Street.COMPLETE;
    }

    private void goToShowdown() {
        street = Street.SHOWDOWN;
        actingIndex = -1;
        showdown = true;

        Map<String, Long> contributions = new LinkedHashMap<>();
        Set<String> folded = new HashSet<>();
        for (int i = 0; i < n; i++) {
            Player p = players.get(i);
            contributions.put(p.id(), committedTotal[i]);
            if (p.status() == PlayerStatus.FOLDED) {
                folded.add(p.id());
            }
        }
        pots = SidePots.build(contributions, folded);

        for (Pot pot : pots) {
            awardPot(pot);
        }
        street = Street.COMPLETE;
    }

    private void awardPot(Pot pot) {
        // 자격자 중 최강 핸드 찾기
        HandRank best = null;
        List<Player> winners = new ArrayList<>();
        for (String id : pot.eligiblePlayerIds()) {
            Player p = playerById(id);
            List<Card> seven = new ArrayList<>(p.holeCards());
            seven.addAll(board);
            HandRank rank = HandEvaluator.evaluate(seven);
            if (best == null || rank.compareTo(best) > 0) {
                best = rank;
                winners.clear();
                winners.add(p);
            } else if (rank.compareTo(best) == 0) {
                winners.add(p);
            }
        }

        long share = pot.amount() / winners.size();
        long remainder = pot.amount() % winners.size();
        // 나머지 칩(홀수 칩)은 버튼 왼쪽부터 순서대로 한 칩씩
        winners.sort((a, b) -> Integer.compare(seatOrderFromButton(a), seatOrderFromButton(b)));
        for (int i = 0; i < winners.size(); i++) {
            long amt = share + (i < remainder ? 1 : 0);
            winners.get(i).addToStack(amt);
            payouts.merge(winners.get(i).id(), amt, Long::sum);
        }
    }

    // ---------------------------------------------------------------- 좌석/순서 계산

    private int sbIndex() {
        return n == 2 ? button : (button + 1) % n;
    }

    private int bbIndex() {
        return n == 2 ? (button + 1) % n : (button + 2) % n;
    }

    private int preflopFirst() {
        return n == 2 ? sbIndex() : (bbIndex() + 1) % n;
    }

    private int postflopFirst() {
        return n == 2 ? bbIndex() : sbIndex();
    }

    /** fromSeat(포함)부터 시계방향으로 액션이 필요한 첫 ACTIVE 좌석. 없으면 -1. */
    private int nextActionable(int fromSeat) {
        for (int i = 0; i < n; i++) {
            int seat = (fromSeat + i) % n;
            if (needsToAct[seat] && players.get(seat).status() == PlayerStatus.ACTIVE) {
                return seat;
            }
        }
        return -1;
    }

    private int countNotFolded() {
        int c = 0;
        for (Player p : players) {
            if (p.status() != PlayerStatus.FOLDED) {
                c++;
            }
        }
        return c;
    }

    private int countCanAct() {
        int c = 0;
        for (Player p : players) {
            if (p.status() == PlayerStatus.ACTIVE) {
                c++;
            }
        }
        return c;
    }

    private int seatOrderFromButton(Player p) {
        int seat = indexOf(p.id());
        return (seat - button - 1 + n) % n;
    }

    private int indexOf(String id) {
        for (int i = 0; i < n; i++) {
            if (players.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalArgumentException("좌석에 없는 플레이어: " + id);
    }

    private Player playerById(String id) {
        return players.get(indexOf(id));
    }

    // ---------------------------------------------------------------- 조회(읽기 전용)

    public Street street() {
        return street;
    }

    public boolean isComplete() {
        return street == Street.COMPLETE;
    }

    public List<Card> board() {
        return List.copyOf(board);
    }

    public long pot() {
        return Arrays.stream(committedTotal).sum();
    }

    public List<Pot> pots() {
        return List.copyOf(pots);
    }

    /** 종료 후 플레이어별 획득 칩(팟 분배 결과). */
    public Map<String, Long> payouts() {
        return Collections.unmodifiableMap(payouts);
    }

    /** 현재 액션할 플레이어(없으면 null). */
    public Player playerToAct() {
        return actingIndex == -1 ? null : players.get(actingIndex);
    }

    public long amountToCall(String playerId) {
        int seat = indexOf(playerId);
        return Math.max(0, currentBet - committedThisStreet[seat]);
    }

    /** 최소 레이즈-투 금액(참고용). 실제로는 스택에 따라 올인만 가능할 수 있다. */
    public long minRaiseTo() {
        return currentBet + lastRaiseSize;
    }

    public long currentBet() {
        return currentBet;
    }

    /** 이번 스트리트에 해당 플레이어가 이미 넣은 칩(UI 벳 표시용). */
    public long committedThisStreet(String playerId) {
        return committedThisStreet[indexOf(playerId)];
    }

    /** 쇼다운까지 갔는가(true 여야 폴드 안 한 플레이어 카드를 공개해도 된다). */
    public boolean wentToShowdown() {
        return showdown;
    }

    public int buttonSeat() {
        return button;
    }

    public List<Player> players() {
        return players;
    }

    /** 지금까지의 진행을 이벤트 소싱 기록으로 낸다(리플레이/핸드 히스토리용). */
    public HandLog log() {
        return new HandLog(initialSeats, button, smallBlind, bigBlind, initialDeckOrder, appliedActions);
    }

    /** 현재 액션자가 취할 수 있는 액션 종류. */
    public Set<ActionType> legalActions(String playerId) {
        if (actingIndex == -1 || !players.get(actingIndex).id().equals(playerId)) {
            return Set.of();
        }
        int seat = actingIndex;
        long stack = players.get(seat).stack();
        long toCall = currentBet - committedThisStreet[seat];
        EnumSet<ActionType> set = EnumSet.of(ActionType.FOLD);
        if (toCall <= 0) {
            set.add(ActionType.CHECK);
            if (stack > 0) {
                // 아직 벳이 없으면 BET, 이미 벳을 맞춘 상태(예: BB 옵션)면 RAISE
                if (currentBet == 0) {
                    set.add(ActionType.BET);
                } else if (actionReopenable) {
                    set.add(ActionType.RAISE);
                }
            }
        } else {
            set.add(ActionType.CALL);
            if (stack > toCall && actionReopenable) {
                set.add(ActionType.RAISE);
            }
        }
        return set;
    }
}
