import { useEffect, useCallback, useRef } from 'react';
import { signalsApi, type SignalFilters } from '@/api/signals';
import { useTradingStore } from '@/store/tradingStore';

const REFRESH_INTERVAL = parseInt(import.meta.env.VITE_REFRESH_INTERVAL || '30000', 10);

export function useSignals(filters?: SignalFilters) {
  const { setSignals, setSignalsLoading, setSignalsError } = useTradingStore();
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchSignals = useCallback(async () => {
    try {
      const response = await signalsApi.getSignals(filters);
      setSignals(response.signals);
    } catch (err) {
      console.error('[useSignals] fetchSignals error:', err);
      setSignalsError('Failed to fetch trading signals');
    }
  }, [filters, setSignals, setSignalsError]);

  useEffect(() => {
    setSignalsLoading(true);
    fetchSignals().finally(() => setSignalsLoading(false));

    intervalRef.current = setInterval(fetchSignals, REFRESH_INTERVAL);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [fetchSignals, setSignalsLoading]);

  const signals = useTradingStore((state) => state.signals);
  const loading = useTradingStore((state) => state.signalsLoading);
  const error = useTradingStore((state) => state.signalsError);

  return { signals, loading, error, refetch: fetchSignals };
}

export function useSignalById(id: string) {
  const { setSelectedSignal } = useTradingStore();

  const fetchSignal = useCallback(async () => {
    if (!id) return;
    try {
      const signal = await signalsApi.getSignal(id);
      setSelectedSignal(signal);
    } catch (err) {
      console.error(`[useSignalById] id=${id} error:`, err);
    }
  }, [id, setSelectedSignal]);

  useEffect(() => {
    fetchSignal();
  }, [fetchSignal]);

  const signal = useTradingStore((state) => state.selectedSignal);

  return { signal, refetch: fetchSignal };
}
