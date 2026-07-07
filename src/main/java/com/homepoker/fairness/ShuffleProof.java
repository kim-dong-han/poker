package com.homepoker.fairness;

import java.util.List;

/**
 * 핸드 종료 후 공개하는 셔플 증명(리빌).
 * 클라이언트는 SHA-256(salt + ":" + deckOrder 를 ","로 연결) 을 재계산해
 * 핸드 시작 전에 공개된 commitment 와 일치하는지 검증할 수 있다.
 * deckOrder 는 리플레이(HandLog)의 초기 덱과 동일해야 하므로, 보드·홀카드가
 * 이 순서에서 규칙대로 딜렸는지도 대조 가능 — 진행 중 카드 조작이 불가능했음의 증거.
 *
 * @param salt       커밋 시 사용한 무작위 솔트(hex)
 * @param deckOrder  셔플된 52장 전체(맨 앞 = 가장 먼저 딜)
 * @param commitment 핸드 시작 전 공개했던 SHA-256 해시(hex)
 */
public record ShuffleProof(String salt, List<String> deckOrder, String commitment) {

    public ShuffleProof {
        deckOrder = List.copyOf(deckOrder);
    }
}
