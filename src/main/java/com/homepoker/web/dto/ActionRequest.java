package com.homepoker.web.dto;

/** 플레이어 액션 요청. type = FOLD/CHECK/CALL/BET/RAISE, amount 는 BET/RAISE 시 raise-to 총액. */
public record ActionRequest(String type, long amount) {
}
