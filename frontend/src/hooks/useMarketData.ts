import { useEffect, useCallback, useRef } from 'react';
import { marketApi } from '@/api/market';
import { useTradingStore } from '@/store/tradingStore';

const REFRESH_INTERVAL = parseInt(import.meta.env.VITE_REFRESH_INTERVAL || '5000', 10);

export function useMarketData() {
  const {
    setDashboard,
    setDashboardLoading,
    setDashboardError,
    setGlobalMacro,
    setIndiaVix,
  } = useTradingStore();

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchDashboard = useCallback(async () => {
    try {
      const data = await marketApi.getDashboard();
      setDashboard(data);
      if (data.indiaVix !== undefined) {
        setIndiaVix(data.indiaVix, data.indiaVixChange ?? 0);
      }
    } catch (err) {
      console.error('[useMarketData] fetchDashboard error:', err);
      setDashboardError('Failed to fetch dashboard data');
    }
  }, [setDashboard, setIndiaVix, setDashboardError]);

  const fetchGlobalMacro = useCallback(async () => {
    try {
      const data = await marketApi.getGlobalMacro();
      setGlobalMacro(data);
    } catch (err) {
      console.error('[useMarketData] fetchGlobalMacro error:', err);
    }
  }, [setGlobalMacro]);

  const fetchAll = useCallback(async () => {
    await Promise.allSettled([fetchDashboard(), fetchGlobalMacro()]);
  }, [fetchDashboard, fetchGlobalMacro]);

  useEffect(() => {
    setDashboardLoading(true);
    fetchAll().finally(() => setDashboardLoading(false));

    intervalRef.current = setInterval(fetchAll, REFRESH_INTERVAL);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [fetchAll, setDashboardLoading]);

  return { refetch: fetchAll };
}

export function useQuote(symbol: string, exchange: string = 'NSE') {
  const { setMarketData } = useTradingStore();
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchQuote = useCallback(async () => {
    if (!symbol) return;
    try {
      const data = await marketApi.getQuote(symbol, exchange);
      setMarketData(data);
    } catch (err) {
      console.error(`[useQuote] ${symbol} error:`, err);
    }
  }, [symbol, exchange, setMarketData]);

  useEffect(() => {
    fetchQuote();
    intervalRef.current = setInterval(fetchQuote, REFRESH_INTERVAL);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [fetchQuote]);

  const marketData = useTradingStore(
    (state) => state.marketDataMap[`${exchange}:${symbol}`] || null
  );

  return { marketData, refetch: fetchQuote };
}
