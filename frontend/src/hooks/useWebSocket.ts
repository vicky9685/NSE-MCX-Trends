import { useEffect, useRef, useCallback } from 'react';
import { Client, StompSubscription } from '@stomp/stompjs';
import toast from 'react-hot-toast';
import { useTradingStore } from '@/store/tradingStore';
import type { WsMessage } from '@/types';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';
const MAX_RECONNECT_DELAY = 30000;
const BASE_RECONNECT_DELAY = 1000;

export function useWebSocket() {
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<StompSubscription[]>([]);

  const {
    setMarketData,
    setIndiaVix,
    addSignal,
    addAlert,
    setDashboard,
    setWsStatus,
    wsReconnectAttempts,
    incrementReconnectAttempts,
    resetReconnectAttempts,
  } = useTradingStore();

  const getReconnectDelay = useCallback(
    (attempt: number) =>
      Math.min(BASE_RECONNECT_DELAY * Math.pow(2, attempt), MAX_RECONNECT_DELAY),
    []
  );

  const handleMessage = useCallback(
    (body: string) => {
      try {
        const msg: WsMessage = JSON.parse(body);
        switch (msg.type) {
          case 'MARKET_DATA':
            setMarketData(msg.payload as ReturnType<typeof setMarketData extends (data: infer D) => void ? () => D : never>);
            break;
          case 'INDIA_VIX':
            const vixPayload = msg.payload as { value: number; change: number };
            setIndiaVix(vixPayload.value, vixPayload.change);
            break;
          case 'SIGNAL':
            addSignal(msg.payload as Parameters<typeof addSignal>[0]);
            toast.success(`New signal: ${(msg.payload as { symbol: string }).symbol}`, {
              duration: 4000,
              icon: '📊',
            });
            break;
          case 'ALERT':
            addAlert(msg.payload as Parameters<typeof addAlert>[0]);
            const alertPayload = msg.payload as { title: string; severity: string };
            if (alertPayload.severity === 'CRITICAL') {
              toast.error(alertPayload.title, { duration: 6000 });
            } else {
              toast(alertPayload.title, { duration: 4000, icon: '🔔' });
            }
            break;
          case 'DASHBOARD':
            setDashboard(msg.payload as Parameters<typeof setDashboard>[0]);
            break;
          default:
            break;
        }
      } catch (err) {
        console.error('[WS] Failed to parse message:', err);
      }
    },
    [setMarketData, setIndiaVix, addSignal, addAlert, setDashboard]
  );

  const connect = useCallback(() => {
    if (clientRef.current?.connected) return;

    setWsStatus('CONNECTING' as Parameters<typeof setWsStatus>[0]);

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: getReconnectDelay(wsReconnectAttempts),
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        setWsStatus('CONNECTED' as Parameters<typeof setWsStatus>[0]);
        resetReconnectAttempts();

        // Subscribe to topics
        const subs: StompSubscription[] = [];

        subs.push(
          client.subscribe('/topic/market-data', (msg) => {
            handleMessage(msg.body);
          })
        );

        subs.push(
          client.subscribe('/topic/signals', (msg) => {
            handleMessage(msg.body);
          })
        );

        subs.push(
          client.subscribe('/topic/alerts', (msg) => {
            handleMessage(msg.body);
          })
        );

        subs.push(
          client.subscribe('/topic/dashboard', (msg) => {
            handleMessage(msg.body);
          })
        );

        subs.push(
          client.subscribe('/topic/india-vix', (msg) => {
            handleMessage(msg.body);
          })
        );

        subscriptionsRef.current = subs;
      },

      onDisconnect: () => {
        setWsStatus('DISCONNECTED' as Parameters<typeof setWsStatus>[0]);
        subscriptionsRef.current = [];
      },

      onStompError: (frame) => {
        console.error('[WS] STOMP error:', frame);
        setWsStatus('ERROR' as Parameters<typeof setWsStatus>[0]);
        incrementReconnectAttempts();
      },

      onWebSocketError: (evt) => {
        console.error('[WS] WebSocket error:', evt);
        setWsStatus('ERROR' as Parameters<typeof setWsStatus>[0]);
        incrementReconnectAttempts();
      },
    });

    client.activate();
    clientRef.current = client;
  }, [
    wsReconnectAttempts,
    getReconnectDelay,
    handleMessage,
    setWsStatus,
    resetReconnectAttempts,
    incrementReconnectAttempts,
  ]);

  const disconnect = useCallback(() => {
    subscriptionsRef.current.forEach((sub) => {
      try {
        sub.unsubscribe();
      } catch (_) {}
    });
    subscriptionsRef.current = [];
    clientRef.current?.deactivate();
    clientRef.current = null;
    setWsStatus('DISCONNECTED' as Parameters<typeof setWsStatus>[0]);
  }, [setWsStatus]);

  useEffect(() => {
    connect();
    return () => {
      disconnect();
    };
    // Only run on mount/unmount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { connect, disconnect };
}
