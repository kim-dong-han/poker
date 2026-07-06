package com.homepoker.rule;

/** RuleGuard 정책 위반. 컨트롤러에서 잡아 요청자에게 사유를 돌려준다. */
public class RuleViolation extends RuntimeException {
    public RuleViolation(String message) {
        super(message);
    }
}
