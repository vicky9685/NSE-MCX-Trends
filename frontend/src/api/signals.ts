import apiClient from './client';
import type { TradeSignal, Exchange, Segment, SignalType, TimeHorizon } from '@/types';

export interface SignalFilters {
  exchange?: Exchange;
  segment?: Segment;
  signalType?: SignalType;
  timeHorizon?: TimeHorizon;
  minConfidence?: number;
  symbol?: string;
  page?: number;
  pageSize?: number;
}

export interface SignalListResponse {
  signals: TradeSignal[];
  total: number;
  page: number;
  pageSize: number;
}

export const signalsApi = {
  // Get all signals with optional filters
  getSignals: (filters?: SignalFilters) =>
    apiClient
      .get<SignalListResponse>('/api/signals', { params: filters })
      .then((r) => r.data),

  // Get a single signal by ID
  getSignal: (id: string) =>
    apiClient.get<TradeSignal>(`/api/signals/${id}`).then((r) => r.data),

  // Get top signals for dashboard
  getTopSignals: (limit: number = 5) =>
    apiClient
      .get<TradeSignal[]>('/api/signals/top', { params: { limit } })
      .then((r) => r.data),

  // Get signals for a specific symbol
  getSignalsBySymbol: (symbol: string, exchange: Exchange) =>
    apiClient
      .get<TradeSignal[]>('/api/signals/symbol', { params: { symbol, exchange } })
      .then((r) => r.data),

  // Refresh / regenerate signals (triggers multi-agent analysis)
  refreshSignals: () =>
    apiClient.post<{ message: string; jobId: string }>('/api/signals/refresh').then((r) => r.data),

  // Get signal performance history
  getSignalHistory: (days: number = 30) =>
    apiClient
      .get<TradeSignal[]>('/api/signals/history', { params: { days } })
      .then((r) => r.data),

  // Get signal accuracy stats
  getSignalStats: () =>
    apiClient
      .get<{
        totalSignals: number;
        accuracy: number;
        avgRR: number;
        avgConfidence: number;
        byType: Record<string, number>;
      }>('/api/signals/stats')
      .then((r) => r.data),
};
