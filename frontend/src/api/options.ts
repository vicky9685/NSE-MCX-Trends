import apiClient from './client';
import type { OptionChain } from '@/types';

export const optionsApi = {
  // Get full option chain for an underlying
  getOptionChain: (symbol: string, expiry?: string) =>
    apiClient
      .get<OptionChain>('/api/options/chain', { params: { symbol, expiry } })
      .then((r) => r.data),

  // Get available expiry dates for a symbol
  getExpiries: (symbol: string) =>
    apiClient
      .get<string[]>('/api/options/expiries', { params: { symbol } })
      .then((r) => r.data),

  // Get PCR (Put-Call Ratio) history
  getPCRHistory: (symbol: string, days: number = 30) =>
    apiClient
      .get<{ date: string; pcr: number }[]>('/api/options/pcr-history', {
        params: { symbol, days },
      })
      .then((r) => r.data),

  // Get OI build-up analysis
  getOIBuildUp: (symbol: string, expiry?: string) =>
    apiClient
      .get<{
        support: number[];
        resistance: number[];
        maxPain: number;
        pcr: number;
      }>('/api/options/oi-analysis', { params: { symbol, expiry } })
      .then((r) => r.data),

  // Get IV surface data
  getIVSurface: (symbol: string) =>
    apiClient
      .get<{ strike: number; expiry: string; iv: number }[]>('/api/options/iv-surface', {
        params: { symbol },
      })
      .then((r) => r.data),
};
