package com.homepoker.equity;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.card.Rank;
import com.homepoker.engine.card.Suit;
import com.homepoker.engine.eval.HandEvaluator;
import com.homepoker.engine.eval.HandRank;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 몬테카를로 이퀴티 추정기. 히어로의 홀카드 2장과 공개된 보드가 주어졌을 때,
 * 미지의 상대(들)에게 무작위 카드를 나눠주고 남은 보드를 채워 수천 번 대결시켜
 * 승/무/이퀴티를 추정한다. HandEvaluator 를 그대로 재사용한다.
 *
 * 서버는 각 플레이어에게 "본인 이퀴티만" 계산해 보내므로 상대 카드 정보가 유출되지 않는다.
 */
@Service
public class EquityService {

    private static final int DEFAULT_ITERATIONS = 3000;

    /** 52장 전체(불변). */
    private static final List<Card> FULL_DECK = buildFullDeck();

    private static List<Card> buildFullDeck() {
        List<Card> all = new ArrayList<>(52);
        for (Suit s : Suit.values()) {
            for (Rank r : Rank.values()) {
                all.add(new Card(r, s));
            }
        }
        return List.copyOf(all);
    }

    public Equity estimate(List<Card> hero, List<Card> board, int opponents) {
        return estimate(hero, board, opponents, DEFAULT_ITERATIONS, new Random());
    }

    /**
     * @param hero      히어로 홀카드 2장
     * @param board     현재 공개된 보드(0~5장)
     * @param opponents 미지의 상대 수(>=1)
     * @param iterations 시뮬레이션 횟수
     * @param rng       난수원(테스트 재현성을 위해 주입)
     */
    public Equity estimate(List<Card> hero, List<Card> board, int opponents,
                           int iterations, Random rng) {
        if (hero.size() != 2) {
            throw new IllegalArgumentException("히어로 홀카드는 2장이어야 한다");
        }
        if (board.size() > 5) {
            throw new IllegalArgumentException("보드는 최대 5장");
        }
        if (opponents < 1) {
            throw new IllegalArgumentException("상대는 1명 이상이어야 한다");
        }

        Set<Card> known = new HashSet<>(hero);
        known.addAll(board);
        if (known.size() != hero.size() + board.size()) {
            throw new IllegalArgumentException("히어로/보드에 중복 카드가 있다");
        }

        List<Card> deck = new ArrayList<>(FULL_DECK);
        deck.removeAll(known);

        int boardNeeded = 5 - board.size();
        int drawPerIter = opponents * 2 + boardNeeded;
        if (drawPerIter > deck.size()) {
            throw new IllegalArgumentException("남은 카드가 부족하다(상대 수가 너무 많음)");
        }

        int wins = 0;
        int ties = 0;
        double equitySum = 0.0;

        for (int it = 0; it < iterations; it++) {
            // 필요한 만큼만 Fisher-Yates 로 앞에서 뽑는다.
            partialShuffle(deck, drawPerIter, rng);

            int idx = 0;
            List<Card> fullBoard = new ArrayList<>(board);
            for (int b = 0; b < boardNeeded; b++) {
                fullBoard.add(deck.get(idx++));
            }

            List<Card> heroSeven = new ArrayList<>(hero);
            heroSeven.addAll(fullBoard);
            HandRank heroRank = HandEvaluator.evaluate(heroSeven);

            HandRank bestOpp = null;
            int tiedOpponents = 0;
            for (int o = 0; o < opponents; o++) {
                List<Card> oppSeven = new ArrayList<>(4);
                oppSeven.add(deck.get(idx++));
                oppSeven.add(deck.get(idx++));
                oppSeven.addAll(fullBoard);
                HandRank oppRank = HandEvaluator.evaluate(oppSeven);
                int cmp = bestOpp == null ? 1 : oppRank.compareTo(bestOpp);
                if (cmp > 0) {
                    bestOpp = oppRank;
                    tiedOpponents = 1;
                } else if (cmp == 0) {
                    tiedOpponents++;
                }
            }

            int heroVsBest = heroRank.compareTo(bestOpp);
            if (heroVsBest > 0) {
                wins++;
                equitySum += 1.0;
            } else if (heroVsBest == 0) {
                ties++;
                equitySum += 1.0 / (1 + tiedOpponents); // 히어로 + 동점 상대 수로 찹
            }
            // heroVsBest < 0 → 패, 지분 0
        }

        return new Equity((double) wins / iterations, (double) ties / iterations,
                equitySum / iterations, iterations);
    }

    /** 리스트 앞쪽 count 개만 무작위로 채우는 부분 셔플(전체 셔플보다 빠름). */
    private static void partialShuffle(List<Card> list, int count, Random rng) {
        int n = list.size();
        for (int i = 0; i < count; i++) {
            int j = i + rng.nextInt(n - i);
            Collections.swap(list, i, j);
        }
    }
}
