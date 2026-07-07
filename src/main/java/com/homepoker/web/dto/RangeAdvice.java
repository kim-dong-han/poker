package com.homepoker.web.dto;

/** 프리플랍 오픈 조언: 이 포지션에서 이 핸드가 표준 오픈 레인지에 드는가. */
public record RangeAdvice(String position, String hand, boolean shouldOpen) {
}
