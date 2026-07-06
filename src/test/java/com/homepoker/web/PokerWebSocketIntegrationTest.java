package com.homepoker.web;

import com.homepoker.web.dto.JoinRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 실제 WebSocket/STOMP 전송 계층 end-to-end 검증:
 * 핸드셰이크 principal(playerId) → 유저 목적지 개인 전송 → 상대 홀카드 리댁션까지.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PokerWebSocketIntegrationTest {

    @Value("${local.server.port}")
    int port;

    private StompSession connect(String playerId) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        String url = "ws://localhost:" + port + "/ws-native?playerId=" + playerId;
        return client.connectAsync(url, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);
    }

    private static StompFrameHandler mapHandler(BlockingQueue<Map<String, Object>> sink) {
        return new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                sink.add((Map<String, Object>) payload);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> seat(Map<String, Object> view, String playerId) {
        List<Map<String, Object>> seats = (List<Map<String, Object>>) view.get("seats");
        return seats.stream().filter(s -> playerId.equals(s.get("playerId"))).findFirst().orElseThrow();
    }

    /** 조건을 만족하는 프레임이 올 때까지 최대 5초 폴링. */
    private static Map<String, Object> await(BlockingQueue<Map<String, Object>> sink,
                                             java.util.function.Predicate<Map<String, Object>> cond) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> frame = sink.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (frame != null && cond.test(frame)) {
                return frame;
            }
        }
        fail("기대한 상태 프레임이 5초 내에 오지 않음");
        return null; // unreachable
    }

    @Test
    void twoPlayersPlayOverWebSocketWithRedaction() throws Exception {
        BlockingQueue<Map<String, Object>> aliceFrames = new LinkedBlockingQueue<>();
        BlockingQueue<Map<String, Object>> bobFrames = new LinkedBlockingQueue<>();

        StompSession alice = connect("alice");
        alice.subscribe("/user/queue/table.tw", mapHandler(aliceFrames));
        StompSession bob = connect("bob");
        bob.subscribe("/user/queue/table.tw", mapHandler(bobFrames));

        alice.send("/app/table/tw/join", new JoinRequest("Alice", 1000));
        bob.send("/app/table/tw/join", new JoinRequest("Bob", 1000));

        // 두 명 착석이 브로드캐스트로 확인될 때까지 대기(경합 방지)
        await(aliceFrames, v -> ((List<?>) v.get("seats")).size() == 2);

        alice.send("/app/table/tw/start", "");

        // 앨리스 관점: 핸드 진행 중 + 본인 카드 2장 보임 + 밥 카드 숨김
        Map<String, Object> aliceView = await(aliceFrames, v -> Boolean.TRUE.equals(v.get("handInProgress")));
        assertEquals("PREFLOP", aliceView.get("street"));
        assertNotNull(seat(aliceView, "alice").get("holeCards"));
        assertEquals(2, ((List<?>) seat(aliceView, "alice").get("holeCards")).size());
        assertNull(seat(aliceView, "bob").get("holeCards"), "상대 홀카드가 전송선에서 노출됨");

        // 밥 관점: 본인 카드 보임, 앨리스 카드 숨김
        Map<String, Object> bobView = await(bobFrames, v -> Boolean.TRUE.equals(v.get("handInProgress")));
        assertNotNull(seat(bobView, "bob").get("holeCards"));
        assertNull(seat(bobView, "alice").get("holeCards"));

        alice.disconnect();
        bob.disconnect();
    }
}
