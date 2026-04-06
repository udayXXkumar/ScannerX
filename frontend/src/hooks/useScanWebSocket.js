import { useEffect, useState, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { workspaceQueryKeys } from '../lib/workspaceQueryKeys';

const configuredWebSocketBaseUrl = import.meta.env.VITE_WS_BASE_URL?.trim();

const buildSockJsUrl = () => {
  if (!configuredWebSocketBaseUrl) {
    return '/ws/scans';
  }

  return `${configuredWebSocketBaseUrl.replace(/\/+$/, '')}/ws/scans`;
};

const ensureSockJsBrowserGlobals = () => {
  if (typeof window === 'undefined') {
    return;
  }

  // sockjs-client still expects a Node-style global in some browser bundles.
  if (typeof globalThis.global === 'undefined') {
    globalThis.global = window;
  }
};

export const useScanWebSocket = (scanId) => {
  const [events, setEvents] = useState([]);
  const [isConnected, setIsConnected] = useState(false);
  const stompClientRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const queryClient = useQueryClient();

  useEffect(() => {
    setEvents([]);
    if (!scanId) return;

    let isCancelled = false;

    const connect = async () => {
      try {
        ensureSockJsBrowserGlobals();
        const [{ default: SockJS }, { Stomp }] = await Promise.all([
          import('sockjs-client'),
          import('@stomp/stompjs'),
        ]);

        if (isCancelled) {
          return;
        }

        const socket = new SockJS(buildSockJsUrl());
        const stompClient = Stomp.over(socket);
        stompClientRef.current = stompClient;

        stompClient.debug = () => {};

        stompClient.connect(
          {},
          () => {
            if (isCancelled) {
              return;
            }

            setIsConnected(true);
            stompClient.subscribe(`/topic/scans/${scanId}`, (msg) => {
              if (msg.body) {
                try {
                  const event = JSON.parse(msg.body);
                  setEvents((prev) => [...prev, event]);
                  handleRealtimeInvalidation(queryClient, scanId, event);
                } catch (e) {
                  console.error('Error parsing websocket message', e);
                }
              }
            });
          },
          (error) => {
            if (isCancelled) {
              return;
            }

            console.error('STOMP error, reconnecting in 3s...', error);
            setIsConnected(false);

            if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
            reconnectTimeoutRef.current = setTimeout(connect, 3000);
          },
        );
      } catch (error) {
        if (!isCancelled) {
          console.error('Failed to initialize websocket client', error);
          setIsConnected(false);
        }
      }
    };

    connect();

    return () => {
      isCancelled = true;
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (stompClientRef.current?.connected) {
        stompClientRef.current.disconnect();
      }
    };
  }, [queryClient, scanId]);

  return { events, isConnected, setEvents };
};

const handleRealtimeInvalidation = (queryClient, scanId, event) => {
  if (!event?.type) {
    return
  }

  if (event.type === 'SCAN_PROGRESS' || event.type === 'SCAN_STATUS') {
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scan(scanId) })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scans })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.targets })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboardSummary })
    return
  }

  if (event.type === 'FINDING_FOUND') {
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scanReport(scanId) })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.findings })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboardSummary })
    return
  }

  if (event.type === 'FINDING_ENRICHED') {
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scanReport(scanId) })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.findings })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboardSummary })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.reportSummary })
    return
  }

  if (
    event.type === 'SCAN_COMPLETED' ||
    event.type === 'SCAN_FAILED' ||
    event.type === 'SCAN_CANCELLED' ||
    event.type === 'SCAN_PAUSED' ||
    event.type === 'SCAN_RESUMED' ||
    event.type === 'PAUSE_REQUESTED'
  ) {
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scan(scanId) })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scanReport(scanId) })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scanActivity(scanId) })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scans })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.targets })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.findings })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboardSummary })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.reportSummary })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.notifications })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.unreadNotifications })
  }
}
