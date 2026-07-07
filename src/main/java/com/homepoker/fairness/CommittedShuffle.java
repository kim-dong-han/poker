package com.homepoker.fairness;

import com.homepoker.engine.card.Card;
import com.homepoker.engine.card.Rank;
import com.homepoker.engine.card.Suit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

/**
 * 검증 가능한 셔플(commit-reveal). 핸드 시작 전 "셔플 결과의 해시(commitment)"만 공개하고,
 * 핸드 종료 후 솔트+덱 순서를 공개(reveal)하면 누구나 해시를 재계산해 셔플이
 * 핸드 도중 바뀌지 않았음을 증명할 수 있다. 한국 유저의 "조작 아니냐" 불신을 정면으로 치는 장치.
 *
 * commitment = SHA-256( salt + ":" + "As,Kd,..."(덱 52장 표기를 ,로 연결) ) 의 소문자 hex.
 *
 * @param order      셔플된 52장(맨 앞 = 가장 먼저 딜)
 * @param salt       무작위 솔트(hex) — 덱이 52!가지뿐이라 솔트 없이는 사전공격이 가능하다
 * @param commitment 위 규칙의 SHA-256 hex
 */
public record CommittedShuffle(List<Card> order, String salt, String commitment) {

    public CommittedShuffle {
        order = List.copyOf(order);
    }

    /** 새 셔플 생성. 솔트·셔플 모두 SecureRandom. */
    public static CommittedShuffle create() {
        return create(new SecureRandom());
    }

    /** 난수원 주입 버전(테스트 재현성). */
    public static CommittedShuffle create(Random rng) {
        List<Card> all = new ArrayList<>(52);
        for (Suit s : Suit.values()) {
            for (Rank r : Rank.values()) {
                all.add(new Card(r, s));
            }
        }
        Collections.shuffle(all, rng);
        byte[] saltBytes = new byte[16];
        rng.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        return new CommittedShuffle(all, salt, commitmentOf(salt, notations(all)));
    }

    /** 검증용 재계산: 공개된 salt + 덱 순서로 commitment 를 다시 만든다. */
    public static String commitmentOf(String salt, List<String> deckNotations) {
        String payload = salt + ":" + String.join(",", deckNotations);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 JVM", e); // 표준 필수 알고리즘이라 도달 불가
        }
    }

    /** 종료 후 공개할 증명. */
    public ShuffleProof proof() {
        return new ShuffleProof(salt, notations(order), commitment);
    }

    private static List<String> notations(List<Card> cards) {
        return cards.stream().map(Card::toString).toList();
    }
}
