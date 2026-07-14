package com.homepoker.bot;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.card.Deck;
import com.homepoker.engine.game.Action;
import com.homepoker.engine.game.HandEngine;
import com.homepoker.engine.game.Player;
import com.homepoker.equity.EquityService;
import com.homepoker.range.BtsPreflopCharts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotBrainTest {

    private static List<Card> cards(String... notations) {
        List<Card> list = new ArrayList<>();
        for (String s : notations) {
            list.add(Card.of(s));
        }
        return list;
    }

    private final BotBrain brain = new BotBrain(new EquityService());

    /** 차트+해링턴 규칙까지 전부 켠 운영 구성의 두뇌(올인 우회 회귀 테스트용). */
    private static BotBrain fullBrain() {
        return new BotBrain(new EquityService(),
                new PreflopAdvisor(new BtsPreflopCharts("preflop/bts-preflop-test.json"), 0.35, 0.5),
                new PostflopAdvisor(0.7, 0.4, 0.8));
    }

    // 아무 패 프리플랍 올인 응징: 차트(3벳/폴드)는 상대 올인 시 레이즈 불가라 AA 조차
    // 폴드하던 버그 → 올인 대치는 차트를 우회해 이퀴티로 콜/레이즈해야 한다.
    @Test
    void punishesAnyTwoPreflopShoveWithStrongHand() {
        Player human = new Player("me", "Me", 1000);
        Player bot = new Player("ai-1", "AI 1", 1000);
        // me(버튼/SB) 7h2c 오픈 올인, bot(BB) AhAd
        Deck deck = Deck.ofOrder(cards("7h", "Ah", "2c", "Ad", "Ks", "Qs", "Js", "5d", "9c"));
        HandEngine e = new HandEngine(List.of(human, bot), 0, 10, 20, deck);
        e.start();
        e.apply(Action.raiseTo("me", 1000));

        BotBrain.Decision d = fullBrain().decide(e, "ai-1", 3000, new Random(42));
        assertNotEquals("FOLD", d.type(), "AA 는 아무 패 올인을 응징해야 한다: " + d.reason());
    }

    // 반대 방향의 안전장치: 올인이라고 무조건 콜하는 게 아니라, 쓰레기 핸드는 여전히 폴드.
    @Test
    void stillFoldsJunkFacingPreflopShove() {
        Player human = new Player("me", "Me", 1000);
        Player bot = new Player("ai-1", "AI 1", 1000);
        // me(버튼/SB) AhAd 오픈 올인, bot(BB) 7h2c
        Deck deck = Deck.ofOrder(cards("Ah", "7h", "Ad", "2c", "Ks", "Qs", "Js", "5d", "9c"));
        HandEngine e = new HandEngine(List.of(human, bot), 0, 10, 20, deck);
        e.start();
        e.apply(Action.raiseTo("me", 1000));

        BotBrain.Decision d = fullBrain().decide(e, "ai-1", 3000, new Random(42));
        assertEquals("FOLD", d.type(), "72o 는 올인에도 폴드: " + d.reason());
    }

    // 리버 올인 응징: 해링턴 "리버 큰 벳 = 원페어 폴드" 규칙이 올인에 100% 적용되면
    // 아무 패 올인에 착취당한다 → 올인 대치는 규칙을 우회해 이퀴티로 콜해야 한다.
    @Test
    void callsRiverShoveWithTopPairInsteadOfRuleFold() {
        Player human = new Player("me", "Me", 1000);
        Player bot = new Player("ai-1", "AI 1", 1000);
        // me(버튼/SB) 7h2c, bot(BB) KhQc — 보드 Ks 9d 4h 5d 9s(봇 = 톱페어+보드페어)
        Deck deck = Deck.ofOrder(cards("7h", "Kh", "2c", "Qc", "Ks", "9d", "4h", "5d", "9s"));
        HandEngine e = new HandEngine(List.of(human, bot), 0, 10, 20, deck);
        e.start();
        e.apply(Action.call("me"));
        e.apply(Action.check("ai-1"));
        e.apply(Action.check("ai-1")); // 플랍(포스트플랍 첫 액션 = BB)
        e.apply(Action.check("me"));
        e.apply(Action.check("ai-1")); // 턴
        e.apply(Action.check("me"));
        e.apply(Action.check("ai-1")); // 리버
        e.apply(Action.bet("me", 980)); // 사람 올인

        BotBrain.Decision d = fullBrain().decide(e, "ai-1", 3000, new Random(42));
        assertEquals("CALL", d.type(), "톱페어는 리버 올인을 이퀴티로 콜해야 한다: " + d.reason());
    }

    // 7하이로 플랍 올인을 맞으면(필요이퀴티 ≈ 49%, 실제 ≈ 10%대) 폴드해야 한다.
    @Test
    void foldsTrashFacingHugeBet() {
        Player bot = new Player("ai-1", "AI 1", 1000);
        Player human = new Player("me", "Me", 1000);
        // SB(ai-1) 7h2c, BB(me) AhAd, 보드 Ks Qs Js
        Deck deck = Deck.ofOrder(cards("7h", "Ah", "2c", "Ad", "Ks", "Qs", "Js", "5d", "9c"));
        HandEngine e = new HandEngine(List.of(bot, human), 0, 10, 20, deck);
        e.start();
        e.apply(Action.call("ai-1"));
        e.apply(Action.check("me"));
        e.apply(Action.bet("me", 980)); // 플랍에서 사람이 올인

        BotBrain.Decision d = brain.decide(e, "ai-1", 3000, new Random(42));
        assertEquals("FOLD", d.type());
    }

    // 리버에서 낫플러시급(탑셋 이상) 이퀴티로 공짜 지점이면 체크가 아니라 벳해야 한다.
    @Test
    void betsStrongHandWhenCheckIsFree() {
        Player bot = new Player("ai-1", "AI 1", 1000);
        Player human = new Player("me", "Me", 1000);
        // SB(ai-1) AsAh(탑셋: 보드에 Ad), BB(me) 7c2d, 보드 Ad Ks 4h
        Deck deck = Deck.ofOrder(cards("As", "7c", "Ah", "2d", "Ad", "Ks", "4h", "5d", "9c"));
        HandEngine e = new HandEngine(List.of(bot, human), 0, 10, 20, deck);
        e.start();
        e.apply(Action.call("ai-1"));
        e.apply(Action.check("me"));
        // 플랍(포스트플랍 첫 액션자는 BB=me), 체크가 돌아 봇 차례
        e.apply(Action.check("me"));

        BotBrain.Decision d = brain.decide(e, "ai-1", 3000, new Random(42));
        assertEquals("BET", d.type());
        assertTrue(d.amount() >= 20 && d.amount() <= 1000,
                "벳 금액은 [최소벳, 스택] 범위: " + d.amount());
    }

    // 밸류벳 기준은 인원수 비례: 4인 팟의 AKs(이퀴티 ≈47%)는 고정 62% 기준이면 체크지만
    // 공평 지분(25%) 대비 압도적이므로 레이즈해야 한다(풀테이블 림프-체크 파티 방지).
    @Test
    void valueBarScalesWithOpponentCount() {
        Player p0 = new Player("p0", "P0", 1000);      // BTN
        Player p1 = new Player("p1", "P1", 1000);      // SB
        Player bot = new Player("ai-1", "AI 1", 1000); // BB
        Player p3 = new Player("p3", "P3", 1000);      // UTG
        // 딜 순서 = SB(p1), BB(bot), p3, p0 라운드×2 → bot = AhKh
        Deck deck = Deck.ofOrder(cards("2c", "Ah", "3s", "4d", "7d", "Kh", "8c", "9s",
                "Qs", "Jd", "5c", "6h", "Tc"));
        HandEngine e = new HandEngine(List.of(p0, p1, bot, p3), 0, 10, 20, deck);
        e.start();
        e.apply(Action.call("p3"));
        e.apply(Action.call("p0"));
        e.apply(Action.call("p1")); // 전원 림프 → bot(BB) 옵션

        BotBrain.Decision d = brain.decide(e, "ai-1", 3000, new Random(42));
        assertEquals("RAISE", d.type());
    }

    // 탑셋 에이스로 작은 벳을 맞으면 절대 폴드하지 않는다(콜 또는 레이즈).
    @Test
    void neverFoldsMonsterGettingGreatOdds() {
        Player bot = new Player("ai-1", "AI 1", 1000);
        Player human = new Player("me", "Me", 1000);
        Deck deck = Deck.ofOrder(cards("As", "7c", "Ah", "2d", "Ad", "Ks", "4h", "5d", "9c"));
        HandEngine e = new HandEngine(List.of(bot, human), 0, 10, 20, deck);
        e.start();
        e.apply(Action.call("ai-1"));
        e.apply(Action.check("me"));
        e.apply(Action.bet("me", 30)); // 플랍 소액 벳

        BotBrain.Decision d = brain.decide(e, "ai-1", 3000, new Random(42));
        assertNotEquals("FOLD", d.type());
        assertNotEquals("CHECK", d.type()); // 맞출 벳이 있으므로 체크 불가
    }
}
