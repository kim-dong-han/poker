package com.homepoker.web;

import java.security.Principal;

/** WebSocket 세션에 붙는 최소 principal. name = playerId. */
public record StompPrincipal(String name) implements Principal {
    @Override
    public String getName() {
        return name;
    }
}
