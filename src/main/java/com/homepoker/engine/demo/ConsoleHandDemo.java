package com.homepoker.engine.demo;

import com.homepoker.engine.card.Deck;
import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.ActionType;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.Player;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * WebSocket/Spring 없이 순수 도메인만으로 한 판을 끝까지 돌려 콘솔에 출력하는 데모.
 * 계획서의 "콘솔/테스트로 한 판 끝까지" 확인용.
 *
 * 실행:  ./gradlew runDemo   (또는 IDE 에서 main 실행)
 */
public final class ConsoleHandDemo {

    private ConsoleHandDemo() {
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : new Random().nextLong();
        Random rng = new Random(seed);
        System.out.println("=== 홈포커 콘솔 데모 (seed=" + seed + ") ===");

        List<Player> players = List.of(
                new Player("A", "Alice", 1000),
                new Player("B", "Bob", 1000),
                new Player("C", "Carol", 1000));
        HandEngine engine = new HandEngine(players, 0, 10, 20, Deck.shuffled(new Random(seed)));
        engine.start();

        printHoleCards(players);
        var lastStreet = engine.street();
        System.out.println("[" + lastStreet + "] 보드: " + engine.board() + "  팟: " + engine.pot());

        while (!engine.isComplete()) {
            if (engine.street() != lastStreet) {
                lastStreet = engine.street();
                System.out.println("[" + lastStreet + "] 보드: " + engine.board() + "  팟: " + engine.pot());
            }
            Player actor = engine.playerToAct();
            Set<ActionType> legal = engine.legalActions(actor.id());
            Action action = decide(engine, actor, legal, rng);
            System.out.printf("  %s → %s%s%n", actor.name(), action.type(),
                    action.amount() > 0 ? " " + action.amount() : "");
            engine.apply(action);
        }

        System.out.println("--- 쇼다운 ---");
        System.out.println("보드: " + engine.board());
        engine.pots().forEach(p -> System.out.println("팟 " + p.amount() + " / 자격: " + p.eligiblePlayerIds()));
        System.out.println("획득: " + engine.payouts());
        players.forEach(p -> System.out.println("  " + p.name() + " 최종 스택: " + p.stack()));
    }

    private static void printHoleCards(List<Player> players) {
        players.forEach(p -> System.out.println("  " + p.name() + " 홀: " + p.holeCards()));
    }

    /** 데모용 단순 봇: 대체로 콜/체크, 가끔 올인/폴드. */
    private static Action decide(HandEngine engine, Player actor, Set<ActionType> legal, Random rng) {
        int roll = rng.nextInt(100);
        long committed = engine.currentBet() - engine.amountToCall(actor.id());
        if (roll < 10 && legal.contains(ActionType.BET)) {
            return Action.bet(actor.id(), Math.min(actor.stack(), 60));
        }
        if (roll < 6 && legal.contains(ActionType.RAISE)) {
            return Action.raiseTo(actor.id(), Math.min(committed + actor.stack(), engine.minRaiseTo()));
        }
        if (roll < 12 && legal.contains(ActionType.FOLD) && !legal.contains(ActionType.CHECK)) {
            return Action.fold(actor.id());
        }
        if (legal.contains(ActionType.CHECK)) {
            return Action.check(actor.id());
        }
        if (legal.contains(ActionType.CALL)) {
            return Action.call(actor.id());
        }
        return Action.fold(actor.id());
    }
}
