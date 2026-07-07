package com.homepoker.fairness;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CommittedShuffleTest {

    // 공개된 solt+덱 순서로 해시를 재계산하면 커밋과 정확히 일치한다(검증 성립).
    @Test
    void revealedProofRecomputesToSameCommitment() {
        CommittedShuffle shuffle = CommittedShuffle.create(new Random(42));
        ShuffleProof proof = shuffle.proof();

        assertEquals(shuffle.commitment(), proof.commitment());
        assertEquals(proof.commitment(),
                CommittedShuffle.commitmentOf(proof.salt(), proof.deckOrder()));
    }

    // 덱 순서나 솔트가 한 글자라도 다르면 해시가 달라진다(조작 감지).
    @Test
    void tamperedDeckOrSaltChangesCommitment() {
        CommittedShuffle shuffle = CommittedShuffle.create(new Random(42));
        ShuffleProof proof = shuffle.proof();

        List<String> tampered = new java.util.ArrayList<>(proof.deckOrder());
        String swap = tampered.get(0);
        tampered.set(0, tampered.get(1));
        tampered.set(1, swap);
        assertNotEquals(proof.commitment(),
                CommittedShuffle.commitmentOf(proof.salt(), tampered));

        assertNotEquals(proof.commitment(),
                CommittedShuffle.commitmentOf("00" + proof.salt().substring(2), proof.deckOrder()));
    }

    // 고정 벡터: 브라우저(JS crypto.subtle)와 동일 규칙 SHA-256(salt + ":" + "As,Kd").
    // 값은 node crypto 로 독립 계산 — 서버/클라이언트 해시 규칙이 갈라지면 여기서 깨진다.
    @Test
    void commitmentMatchesCrossLanguageFixedVector() {
        assertEquals("089dacbdadcd1f7f1ecb64d20c422d7420afd9b8e3a86d3a637bc6ec56dac4b8",
                CommittedShuffle.commitmentOf("ab", List.of("As", "Kd")));
    }

    // 덱은 항상 중복 없는 52장 전체 순열이다.
    @Test
    void deckIsFullPermutationOf52() {
        CommittedShuffle shuffle = CommittedShuffle.create(new Random(7));
        assertEquals(52, shuffle.order().size());
        assertEquals(52, new HashSet<>(shuffle.order()).size());
    }

    // 매 핸드 새 솔트·새 셔플 — 같은 덱이어도 커밋이 재사용되지 않는다.
    @Test
    void eachShuffleGetsFreshSaltAndCommitment() {
        CommittedShuffle a = CommittedShuffle.create(new Random(1));
        CommittedShuffle b = CommittedShuffle.create(new Random(2));
        assertNotEquals(a.salt(), b.salt());
        assertNotEquals(a.commitment(), b.commitment());
    }
}
