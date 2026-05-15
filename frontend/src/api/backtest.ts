import apiClient from './client';

// ─── Types ────────────────────────────────────────────────────────────────────

export type BacktestStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
export type TradeDirection = 'LONG' | 'SHORT';
export type ExitReason = 'TARGET_HIT' | 'SL_HIT' | 'SIGNAL' | 'EXPIRED' | 'END_OF_PERIOD';

export interface BacktestResult {
  id: number;
  name: string;
  strategyName: string;
  instrumentId: number;
  instrumentSymbol?: string;
  startDate: string;
  endDate: string;
  initialCapital: number;
  finalCapital: number;
  totalReturn: number;
  annualizedReturn: number;
  maxDrawdown: number;
  sharpeRatio: number;
  sortinoRatio: number;
  winRate: number;
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  avgWin: number;
  avgLoss: number;
  profitFactor: number;
  commissionPaid: number;
  slippageCost: number;
  parametersJson?: string;
  status: BacktestStatus;
  createdAt: string;
  completedAt?: string;
}

export interface BacktestTrade {
  id: number;
  backtestId: number;
  instrumentId: number;
  symbol?: string;
  entryDate: string;
  exitDate?: string;
  entryPrice: number;
  exitPrice?: number;
  quantity: number;
  direction: TradeDirection;
  pnl?: number;
  pnlPercent?: number;
  exitReason?: ExitReason;
  signalType?: string;
  holdingDays?: number;
  commission?: number;
  slippage?: number;
}

export interface RunBacktestRequest {
  name: string;
  symbol: string;
  strategyName: string;
  startDate: string;
  endDate: string;
  initialCapital: number;
  parameters?: Record<string, unknown>;
}

export interface EquityCurvePoint {
  date: string;
  value: number;
  drawdown?: number;
}

// ─── API ──────────────────────────────────────────────────────────────────────

export const backtestApi = {
  runBacktest: (req: RunBacktestRequest) =>
    apiClient.post<BacktestResult>('/api/backtest/run', req).then((r) => r.data),

  getResults: () =>
    apiClient.get<BacktestResult[]>('/api/backtest/results').then((r) => r.data),

  getResult: (id: number) =>
    apiClient.get<BacktestResult>(`/api/backtest/results/${id}`).then((r) => r.data),

  getTrades: (id: number) =>
    apiClient.get<BacktestTrade[]>(`/api/backtest/results/${id}/trades`).then((r) => r.data),

  getEquityCurve: (id: number) =>
    apiClient.get<EquityCurvePoint[]>(`/api/backtest/results/${id}/equity-curve`).then((r) => r.data),

  deleteResult: (id: number) =>
    apiClient.delete(`/api/backtest/results/${id}`).then((r) => r.data),
};
