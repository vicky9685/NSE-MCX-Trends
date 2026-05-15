import { useState, useEffect, useCallback } from 'react';
import { paperApi } from '@/api/paper';
import type { PaperPortfolio, PaperPerformancePoint, OpenPaperTradeRequest, ClosePaperTradeRequest } from '@/api/paper';
import toast from 'react-hot-toast';

export function usePaperTrading() {
  const [portfolio, setPortfolio] = useState<PaperPortfolio | null>(null);
  const [performance, setPerformance] = useState<PaperPerformancePoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchPortfolio = useCallback(async () => {
    try {
      setLoading(true);
      const [port, perf] = await Promise.all([
        paperApi.getPortfolio(),
        paperApi.getPerformance(30),
      ]);
      setPortfolio(port);
      setPerformance(perf);
      setError(null);
    } catch (err) {
      console.error('[usePaperTrading] fetch error:', err);
      setError('Failed to load paper trading data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPortfolio();
    const interval = setInterval(fetchPortfolio, 15000);
    return () => clearInterval(interval);
  }, [fetchPortfolio]);

  const openTrade = useCallback(async (req: OpenPaperTradeRequest) => {
    try {
      await paperApi.openTrade(req);
      toast.success(`Paper trade opened: ${req.symbol} ${req.direction}`);
      await fetchPortfolio();
      return true;
    } catch {
      toast.error('Failed to open paper trade');
      return false;
    }
  }, [fetchPortfolio]);

  const closeTrade = useCallback(async (id: number, req: ClosePaperTradeRequest) => {
    try {
      await paperApi.closeTrade(id, req);
      toast.success('Trade closed successfully');
      await fetchPortfolio();
      return true;
    } catch {
      toast.error('Failed to close trade');
      return false;
    }
  }, [fetchPortfolio]);

  const resetPortfolio = useCallback(async () => {
    try {
      await paperApi.resetPortfolio();
      toast.success('Paper trading portfolio reset');
      await fetchPortfolio();
      return true;
    } catch {
      toast.error('Failed to reset portfolio');
      return false;
    }
  }, [fetchPortfolio]);

  return {
    portfolio,
    performance,
    loading,
    error,
    refetch: fetchPortfolio,
    openTrade,
    closeTrade,
    resetPortfolio,
  };
}
