package com.homepoker.web.dto;

/** 테이블 착석 요청. playerId 는 WebSocket 핸드셰이크에서 얻은 principal 을 신뢰한다. */
public record JoinRequest(String name, long buyIn) {
}
