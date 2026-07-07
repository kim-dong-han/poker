import { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const TABLE_ID = 't1';

/**
 * 한 화면에서 여러 플레이어를 다루는 테스트용 훅.
 *
 * 서버는 연결마다 principal(playerId)을 신뢰하므로, 플레이어 한 명당 STOMP 연결을 따로 연다.
 * 그래야 "그 사람으로서" 액션을 보낼 수 있다(홀카드 리댁션도 연결별로 개인화됨).
 * 로컬 솔로 테스트에서 2~6인을 혼자 앉히고 진행하기 위한 용도.
 */
export function usePokerTable() {
  const [players, setPlayers] = useState([]);     // [{id, name}] 착석 순서
  const [views, setViews] = useState({});         // id -> 개인화된 테이블 상태
  const [errors, setErrors] = useState({});       // id -> 마지막 에러 메시지
  const [connected, setConnected] = useState({}); // id -> 연결 여부
  const clientsRef = useRef({});                  // id -> STOMP Client

  const addPlayer = useCallback((id, name) => {
    if (!id || clientsRef.current[id]) return; // 빈 값·중복 방지
    const client = new Client({
      webSocketFactory: () => new SockJS(`/ws?playerId=${encodeURIComponent(id)}`),
      reconnectDelay: 2000,
      onConnect: () => {
        setConnected((c) => ({ ...c, [id]: true }));
        client.subscribe(`/user/queue/table.${TABLE_ID}`, (msg) => {
          setViews((v) => ({ ...v, [id]: JSON.parse(msg.body) }));
          setErrors((e) => ({ ...e, [id]: null }));
        });
        client.subscribe('/user/queue/errors', (msg) =>
          setErrors((e) => ({ ...e, [id]: msg.body })));
        // 접속하면 바로 착석
        client.publish({
          destination: `/app/table/${TABLE_ID}/join`,
          body: JSON.stringify({ name: name || id, buyIn: 1000 }),
        });
      },
      onDisconnect: () => setConnected((c) => ({ ...c, [id]: false })),
      onStompError: (f) => setErrors((e) => ({ ...e, [id]: f.headers['message'] || 'STOMP 오류' })),
    });
    client.activate();
    clientsRef.current[id] = client;
    setPlayers((p) => (p.some((x) => x.id === id) ? p : [...p, { id, name: name || id }]));
  }, []);

  const removePlayer = useCallback((id) => {
    clientsRef.current[id]?.deactivate();
    delete clientsRef.current[id];
    setPlayers((p) => p.filter((x) => x.id !== id));
    setViews((v) => { const n = { ...v }; delete n[id]; return n; });
    setErrors((e) => { const n = { ...e }; delete n[id]; return n; });
    setConnected((c) => { const n = { ...c }; delete n[id]; return n; });
  }, []);

  const send = useCallback((id, suffix, body) => {
    clientsRef.current[id]?.publish({
      destination: `/app/table/${TABLE_ID}/${suffix}`,
      body: JSON.stringify(body || {}),
    });
  }, []);

  const startHand = useCallback((id) => send(id, 'start', {}), [send]);
  const act = useCallback((id, type, amount = 0) => send(id, 'action', { type, amount }), [send]);

  // 언마운트 시 모든 연결 정리
  useEffect(() => () => {
    Object.values(clientsRef.current).forEach((c) => c.deactivate());
    clientsRef.current = {};
  }, []);

  return { players, views, errors, connected, addPlayer, removePlayer, startHand, act };
}
