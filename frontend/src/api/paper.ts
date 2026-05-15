import apiClient from './client';
import type { TradeDirection } from './backtest';

// ─── Types ────────────────────────────────────────────────────────────────────

export type PaperTradeStatus = 'OPEN' | 'CLOSED' | 'CANCELLED';

export interface PaperTrade {
  id: number;
  sessionId: string;
  instrumentId: number;
  symbol: string;
  signalId?: number;
  direction: TradeDirection;
  quantity: number;
  entryPrice: number;
  exitPrice?: number;
  ltp?: number;
  entryTime: string;
  exitTime?: string;
  pnl?: number;
  pnlPercent?: number;
  commission: number;
  status: PaperTradeStatus;
  exitReason?: string;
  stoploss?: number;
  target?: number;
  notes?: string;
}

export interface PaperPortfolio {
  sessionId: string;
  initialCapital: number;
  totalCapital: number;
  deployedCapital: number;
  availableCapital: number;
  totalPnl: number;
  totalPnlPercent: number;
  openTrades: PaperTrade[];
  closedTrades: PaperTrade[];
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  winRate: number;
  maxDrawdown: number;
}

export interface PaperPerformancePoint {
  date: string;
  portfolioValue: number;
  pnl: number;
  pnlPercent: number;
}

export interface OpenPaperTradeRequest {
  symbol: string;
  signalId?: number;
  direction: TradeDirection;
  quantity: number;
  entryPrice: number;
  stoploss?: number;
  target?: number;
  notes?: string;
}

export interface ClosePaperTradeRequest {
  exitPrice: number;
  exitReason?: string;
}

export interface PaperSettings {
  initialCapital: number;
  maxPositions: number;
  maxCapitalPerTradePercent: number;
  isEnabled: boolean;
}

// ─── API ──────────────────────────────────────────────────────────────────────

export const paperApi = {
  getPortfolio: () =>
    apiClient.get<PaperPortfolio>('/api/paper/portfolio').then((r) => r.data),

  getPerformance: (days: number = 30) =>
    apiClient
      .get<PaperPerformancePoint[]>('/api/paper/performance', { params: { days } })
      .then((r) => r.data),

  openTrade: (req: OpenPaperTradeRequest) =>
    apiClient.post<PaperTrade>('/api/paper/trades', req).then((r) => r.data),

  closeTrade: (id: number, req: ClosePaperTradeRequest) =>
    apiClient.put<PaperTrade>(`/api/paper/trades/${id}/close`, req).then((r) => r.data),

  cancelTrade: (id: number) =>
    apiClient.put<PaperTrade>(`/api/paper/trades/${id}/cancel`).then((r) => r.data),

  getSettings: () =>
    apiClient.get<PaperSettings>('/api/paper/settings').then((r) => r.data),

  saveSettings: (settings: PaperSettings) =>
    apiClient.post<PaperSettings>('/api/paper/settings', settings).then((r) => r.data),

  resetPortfolio: () =>
    apiClient.post<{ success: boolean }>('/api/paper/reset').then((r) => r.data),
};
