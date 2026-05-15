import { create } from 'zustand';
import { devtools, subscribeWithSelector } from 'zustand/middleware';
import type {
  MarketData,
  TradeSignal,
  MarketDashboard,
  Alert,
  PortfolioPosition,
  PortfolioSummary,
  RiskMetrics,
  GlobalMacroData,
  OptionChain,
  BrokerConfig,
  ConnectionStatus,
} from '@/types';

// ─── State Shape ──────────────────────────────────────────────────────────────

interface TradingState {
  // Market Data
  marketDataMap: Record<string, MarketData>;
  indiaVix: number;
  indiaVixChange: number;

  // Dashboard
  dashboard: MarketDashboard | null;
  dashboardLoading: boolean;
  dashboardError: string | null;

  // Signals
  signals: TradeSignal[];
  signalsLoading: boolean;
  signalsError: string | null;
  selectedSignal: TradeSignal | null;

  // Options
  optionChain: OptionChain | null;
  optionChainLoading: boolean;
  optionChainSymbol: string;
  optionChainExpiry: string;

  // Alerts
  alerts: Alert[];
  unreadAlertsCount: number;
  alertsLoading: boolean;

  // Portfolio
  portfolioPositions: PortfolioPosition[];
  portfolioSummary: PortfolioSummary | null;
  riskMetrics: RiskMetrics | null;
  portfolioLoading: boolean;

  // Global Macro
  globalMacro: GlobalMacroData | null;

  // Broker Configs
  brokerConfigs: BrokerConfig[];

  // WebSocket
  wsStatus: ConnectionStatus;
  wsReconnectAttempts: number;

  // Actions
  setMarketData: (data: MarketData) => void;
  setMarketDataBatch: (data: MarketData[]) => void;
  setIndiaVix: (value: number, change: number) => void;

  setDashboard: (dashboard: MarketDashboard) => void;
  setDashboardLoading: (loading: boolean) => void;
  setDashboardError: (error: string | null) => void;

  setSignals: (signals: TradeSignal[]) => void;
  addSignal: (signal: TradeSignal) => void;
  setSignalsLoading: (loading: boolean) => void;
  setSignalsError: (error: string | null) => void;
  setSelectedSignal: (signal: TradeSignal | null) => void;

  setOptionChain: (chain: OptionChain) => void;
  setOptionChainLoading: (loading: boolean) => void;
  setOptionChainSymbol: (symbol: string) => void;
  setOptionChainExpiry: (expiry: string) => void;

  setAlerts: (alerts: Alert[]) => void;
  addAlert: (alert: Alert) => void;
  updateAlert: (id: string, updates: Partial<Alert>) => void;
  deleteAlert: (id: string) => void;
  markAlertRead: (id: string) => void;
  markAllAlertsRead: () => void;
  setAlertsLoading: (loading: boolean) => void;

  setPortfolioPositions: (positions: PortfolioPosition[]) => void;
  setPortfolioSummary: (summary: PortfolioSummary) => void;
  setRiskMetrics: (metrics: RiskMetrics) => void;
  setPortfolioLoading: (loading: boolean) => void;

  setGlobalMacro: (data: GlobalMacroData) => void;

  setBrokerConfigs: (configs: BrokerConfig[]) => void;
  updateBrokerConfig: (config: BrokerConfig) => void;

  setWsStatus: (status: ConnectionStatus) => void;
  setWsReconnectAttempts: (attempts: number) => void;
  incrementReconnectAttempts: () => void;
  resetReconnectAttempts: () => void;
}

// ─── Store ────────────────────────────────────────────────────────────────────

export const useTradingStore = create<TradingState>()(
  devtools(
    subscribeWithSelector((set, get) => ({
      // Initial State
      marketDataMap: {},
      indiaVix: 0,
      indiaVixChange: 0,

      dashboard: null,
      dashboardLoading: false,
      dashboardError: null,

      signals: [],
      signalsLoading: false,
      signalsError: null,
      selectedSignal: null,

      optionChain: null,
      optionChainLoading: false,
      optionChainSymbol: 'NIFTY',
      optionChainExpiry: '',

      alerts: [],
      unreadAlertsCount: 0,
      alertsLoading: false,

      portfolioPositions: [],
      portfolioSummary: null,
      riskMetrics: null,
      portfolioLoading: false,

      globalMacro: null,

      brokerConfigs: [],

      wsStatus: 'DISCONNECTED' as ConnectionStatus,
      wsReconnectAttempts: 0,

      // Market Data Actions
      setMarketData: (data) =>
        set((state) => ({
          marketDataMap: { ...state.marketDataMap, [`${data.exchange}:${data.symbol}`]: data },
        })),

      setMarketDataBatch: (dataArray) =>
        set((state) => {
          const updates: Record<string, MarketData> = { ...state.marketDataMap };
          dataArray.forEach((d) => {
            updates[`${d.exchange}:${d.symbol}`] = d;
          });
          return { marketDataMap: updates };
        }),

      setIndiaVix: (value, change) => set({ indiaVix: value, indiaVixChange: change }),

      // Dashboard Actions
      setDashboard: (dashboard) => set({ dashboard }),
      setDashboardLoading: (dashboardLoading) => set({ dashboardLoading }),
      setDashboardError: (dashboardError) => set({ dashboardError }),

      // Signal Actions
      setSignals: (signals) => set({ signals }),
      addSignal: (signal) =>
        set((state) => ({
          signals: [signal, ...state.signals.filter((s) => s.id !== signal.id)],
        })),
      setSignalsLoading: (signalsLoading) => set({ signalsLoading }),
      setSignalsError: (signalsError) => set({ signalsError }),
      setSelectedSignal: (selectedSignal) => set({ selectedSignal }),

      // Option Chain Actions
      setOptionChain: (optionChain) => set({ optionChain }),
      setOptionChainLoading: (optionChainLoading) => set({ optionChainLoading }),
      setOptionChainSymbol: (optionChainSymbol) => set({ optionChainSymbol }),
      setOptionChainExpiry: (optionChainExpiry) => set({ optionChainExpiry }),

      // Alert Actions
      setAlerts: (alerts) =>
        set({
          alerts,
          unreadAlertsCount: alerts.filter((a) => !a.isRead).length,
        }),
      addAlert: (alert) =>
        set((state) => {
          const alerts = [alert, ...state.alerts];
          return {
            alerts,
            unreadAlertsCount: alerts.filter((a) => !a.isRead).length,
          };
        }),
      updateAlert: (id, updates) =>
        set((state) => {
          const alerts = state.alerts.map((a) => (a.id === id ? { ...a, ...updates } : a));
          return { alerts, unreadAlertsCount: alerts.filter((a) => !a.isRead).length };
        }),
      deleteAlert: (id) =>
        set((state) => {
          const alerts = state.alerts.filter((a) => a.id !== id);
          return { alerts, unreadAlertsCount: alerts.filter((a) => !a.isRead).length };
        }),
      markAlertRead: (id) => {
        get().updateAlert(id, { isRead: true });
      },
      markAllAlertsRead: () =>
        set((state) => ({
          alerts: state.alerts.map((a) => ({ ...a, isRead: true })),
          unreadAlertsCount: 0,
        })),
      setAlertsLoading: (alertsLoading) => set({ alertsLoading }),

      // Portfolio Actions
      setPortfolioPositions: (portfolioPositions) => set({ portfolioPositions }),
      setPortfolioSummary: (portfolioSummary) => set({ portfolioSummary }),
      setRiskMetrics: (riskMetrics) => set({ riskMetrics }),
      setPortfolioLoading: (portfolioLoading) => set({ portfolioLoading }),

      // Global Macro
      setGlobalMacro: (globalMacro) => set({ globalMacro }),

      // Broker Configs
      setBrokerConfigs: (brokerConfigs) => set({ brokerConfigs }),
      updateBrokerConfig: (config) =>
        set((state) => ({
          brokerConfigs: state.brokerConfigs.map((c) =>
            c.broker === config.broker ? config : c
          ),
        })),

      // WebSocket Actions
      setWsStatus: (wsStatus) => set({ wsStatus }),
      setWsReconnectAttempts: (wsReconnectAttempts) => set({ wsReconnectAttempts }),
      incrementReconnectAttempts: () =>
        set((state) => ({ wsReconnectAttempts: state.wsReconnectAttempts + 1 })),
      resetReconnectAttempts: () => set({ wsReconnectAttempts: 0 }),
    })),
    { name: 'TradingStore' }
  )
);

// ─── Selectors ────────────────────────────────────────────────────────────────

export const selectMarketData = (symbol: string, exchange: string) => (state: TradingState) =>
  state.marketDataMap[`${exchange}:${symbol}`];

export const selectTopSignals = (limit: number) => (state: TradingState) =>
  state.signals.slice(0, limit);

export const selectActiveAlerts = (state: TradingState) =>
  state.alerts.filter((a) => a.isActive && !a.isTriggered);

export const selectTriggeredAlerts = (state: TradingState) =>
  state.alerts.filter((a) => a.isTriggered);
