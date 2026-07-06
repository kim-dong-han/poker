package com.homepoker.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * STOMP over WebSocket 설정.
 *  - 클라이언트 → 서버:  /app/**   (예: /app/table/t1/action)
 *  - 서버 → 특정 유저:   /user/queue/**  (개인화된 리댁션 상태 — 상대 홀카드 유출 방지)
 *
 * 핸드셰이크 시 쿼리파라미터 ?playerId=... 를 principal 로 삼아, 서버가 각 플레이어에게
 * "본인만의 뷰"를 안전하게 보낼 수 있게 한다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        PlayerHandshakeHandler handshake = new PlayerHandshakeHandler();
        // 브라우저용(SockJS)
        registry.addEndpoint("/ws").setHandshakeHandler(handshake)
                .setAllowedOriginPatterns("*").withSockJS();
        // 테스트/네이티브 WebSocket 용(SockJS 없이)
        registry.addEndpoint("/ws-native").setHandshakeHandler(handshake)
                .setAllowedOriginPatterns("*");
    }

    /** ?playerId=... 를 읽어 principal 로 만든다. 없으면 익명 UUID. */
    static final class PlayerHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request,
                                          WebSocketHandler wsHandler,
                                          Map<String, Object> attributes) {
            String query = request.getURI().getQuery();
            String playerId = extractPlayerId(query);
            return new StompPrincipal(playerId != null ? playerId : "anon-" + UUID.randomUUID());
        }

        private static String extractPlayerId(String query) {
            if (query == null) {
                return null;
            }
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0 && part.substring(0, eq).equals("playerId")) {
                    return part.substring(eq + 1);
                }
            }
            return null;
        }
    }
}
