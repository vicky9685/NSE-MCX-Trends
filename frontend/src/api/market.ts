import apiClient from './client';
import type { MarketData, OHLCV, TechnicalIndicator, MarketDashboard, GlobalMacroData, SectorData, MoneyFlow } from '@/types';

export interface QuoteParams {
  symbols: string[];
  exchange?: string;
}

export const marketApi = {
  // Dashboard overview
  getDashboard: () =>
    apiClient.get<MarketDashboard>('/api/market/dashboard').then((r) => r.data),

  // Real-time quote for one or many symbols
  getQuote: (symbol: string, exchange: string = 'NSE') =>
    apiClient
      .get<MarketData>('/api/market/quote', { params: { symbol, exchange } })
      .then((r) => r.data),

  getQuotes: (params: QuoteParams) =>
    apiClient
      .post<MarketData[]>('/api/market/quotes', params)
      .then((r) => r.data),

  // OHLCV candles
  getCandles: (
    symbol: string,
    exchange: string = 'NSE',
    interval: '1m' | '5m' | '15m' | '30m' | '1h' | '1d' | '1w' = '1d',
    from?: string,
    to?: string
  ) =>
    apiClient
      .get<OHLCV[]>('/api/market/candles', {
        params: { symbol, exchange, interval, from, to },
      })
      .then((r) => r.data),

  // Technical indicators
  getIndicators: (symbol: string, exchange: string = 'NSE') =>
    apiClient
      .get<TechnicalIndicator>('/api/market/indicators', {
        params: { symbol, exchange },
      })
      .then((r) => r.data),

  // Market indices
  getIndices: () =>
    apiClient.get<MarketData[]>('/api/market/indices').then((r) => r.data),

  // Sector heatmap
  getSectors: () =>
    apiClient.get<SectorData[]>('/api/market/sectors').then((r) => r.data),

  // FII/DII Money flow
  getMoneyFlow: (days: number = 30) =>
    apiClient
      .get<MoneyFlow[]>('/api/market/money-flow', { params: { days } })
      .then((r) => r.data),

  // Global macro data
  getGlobalMacro: () =>
    apiClient.get<GlobalMacroData>('/api/market/global-macro').then((r) => r.data),

  // India VIX
  getIndiaVix: () =>
    apiClient
      .get<{ value: number; change: number; changePercent: number }>('/api/market/india-vix')
      .then((r) => r.data),

  // Market status
  getMarketStatus: () =>
    apiClient
      .get<{ status: string; nextOpen?: string; nextClose?: string }>('/api/market/status')
      .then((r) => r.data),

  // Top gainers & losers
  getTopMovers: (exchange: string = 'NSE', limit: number = 10) =>
    apiClient
      .get<{ gainers: MarketData[]; losers: MarketData[] }>('/api/market/movers', {
        params: { exchange, limit },
      })
      .then((r) => r.data),
};
