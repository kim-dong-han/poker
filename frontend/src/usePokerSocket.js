import { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const TABLE_ID = 't1';

/**
 * STOMP 연결 훅. playerId 로 접속하면 서버가 개인화된(리댁션된) 테이블 뷰를
 * /user/queue/table.t1 로 밀어준다. 액션은 /app/table/t1/** 로 보낸다.
 */
export function usePokerSocket(playerId, name) {
  const [connected, setConnected] = useState(false);
  const [state, setState] = useState(null);
  const [error, setError] = useState(null);
  const clientRef = useRef(null);

  useEffect(() => {
    if (!playerId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(`/ws?playerId=${encodeURIComponent(playerId)}`),
      reconnectDelay: 2000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/user/queue/table.${TABLE_ID}`, (msg) => {
          setState(JSON.parse(msg.body));
          setError(null);
        });
        client.subscribe('/user/queue/errors', (msg) => setError(msg.body));
        // 접속하면 바로 착석
        client.publish({
          destination: `/app/table/${TABLE_ID}/join`,
          body: JSON.stringify({ name: name || playerId, buyIn: 1000 }),
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: (f) => setError(f.headers['message'] || 'STOMP 오류'),
    });
    client.activate();
    clientRef.current = client;
    return () => client.deactivate();
  }, [playerId, name]);

  const send = useCallback((suffix, body) => {
    clientRef.current?.publish({
      destination: `/app/table/${TABLE_ID}/${suffix}`,
      body: JSON.stringify(body || {}),
    });
  }, []);

  const startHand = useCallback(() => send('start', {}), [send]);
  const act = useCallback((type, amount = 0) => send('action', { type, amount }), [send]);

  return { connected, state, error, startHand, act };
}
