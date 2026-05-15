// ─── Enums ────────────────────────────────────────────────────────────────────

export enum Exchange {
  NSE = 'NSE',
  BSE = 'BSE',
  MCX = 'MCX',
  NFO = 'NFO',
  BFO = 'BFO',
  CDS = 'CDS',
}

export enum Segment {
  EQUITY = 'EQUITY',
  FUTURES = 'FUTURES',
  OPTIONS = 'OPTIONS',
  COMMODITY = 'COMMODITY',
  CURRENCY = 'CURRENCY',
}

export enum SignalType {
  STRONG_BUY = 'STRONG_BUY',
  BUY = 'BUY',
  HOLD = 'HOLD',
  SELL = 'SELL',
  STRONG_SELL = 'STRONG_SELL',
}

export enum TimeHorizon {
  INTRADAY = 'INTRADAY',
  SWING = 'SWING',
  POSITIONAL = 'POSITIONAL',
  LONG_TERM = 'LONG_TERM',
}

export enum AlertType {
  PRICE = 'PRICE',
  TECHNICAL = 'TECHNICAL',
  NEWS = 'NEWS',
  RISK = 'RISK',
  SIGNAL = 'SIGNAL',
  SYSTEM = 'SYSTEM',
}

export enum AlertSeverity {
  INFO = 'INFO',
  WARNING = 'WARNING',
  CRITICAL = 'CRITICAL',
}

export enum OptionType {
  CALL = 'CE',
  PUT = 'PE',
}

export enum BrokerType {
  ZERODHA = 'ZERODHA',
  ALICE_BLUE = 'ALICE_BLUE',
  BONANZA = 'BONANZA',
}

export enum ConnectionStatus {
  CONNECTED = 'CONNECTED',
  DISCONNECTED = 'DISCONNECTED',
  CONNECTING = 'CONNECTING',
  ERROR = 'ERROR',
}

// ─── Core Market Types ────────────────────────────────────────────────────────

export interface Instrument {
  symbol: string;
  name: string;
  exchange: Exchange;
  segment: Segment;
  instrumentToken: number;
  lotSize: number;
  tickSize: number;
  expiryDate?: string;
  strikePrice?: number;
  optionType?: OptionType;
  underlyingSymbol?: string;
  isin?: string;
  series?: string;
}

export interface MarketData {
  symbol: string;
  exchange: Exchange;
  lastTradedPrice: number;
  open: number;
  high: number;
  low: number;
  close: number;
  previousClose: number;
  change: number;
  changePercent: number;
  volume: number;
  averageVolume: number;
  volumeRatio: number;
  bidPrice: number;
  askPrice: number;
  bidQuantity: number;
  askQuantity: number;
  totalBuyQuantity: number;
  totalSellQuantity: number;
  upperCircuit: number;
  lowerCircuit: number;
  fiftyTwoWeekHigh: number;
  fiftyTwoWeekLow: number;
  marketCap?: number;
  lastTradedTime: string;
  timestamp: string;
}

export interface OHLCV {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface TechnicalIndicator {
  symbol: string;
  timestamp: string;
  ema20: number;
  ema50: number;
  ema200: number;
  sma20: number;
  sma50: number;
  rsi14: number;
  macd: number;
  macdSignal: number;
  macdHistogram: number;
  bbUpper: number;
  bbMiddle: number;
  bbLower: number;
  bbWidth: number;
  bbPercentB: number;
  atr14: number;
  adx14: number;
  plusDI: number;
  minusDI: number;
  stochK: number;
  stochD: number;
  williamsR: number;
  cci20: number;
  obv: number;
  vwap: number;
  supertrend: number;
  supertrendDirection: 'UP' | 'DOWN';
}

// ─── Trade Signal ─────────────────────────────────────────────────────────────

export interface TradeSignal {
  id: string;
  symbol: string;
  exchange: Exchange;
  segment: Segment;
  signalType: SignalType;
  confidenceScore: number;           // 0-100
  entryPrice: number;
  target1: number;
  target2: number;
  stopLoss: number;
  riskRewardRatio: number;
  timeHorizon: TimeHorizon;
  rationale: string;
  technicalSummary: string;
  fundamentalContext: string;
  macroAlignment: string;
  hedgingStrategy: string;
  scenarioBull: string;
  scenarioBear: string;
  scenarioBase: string;
  positionSizePercent: number;
  maxRiskPercent: number;
  generatedAt: string;
  validUntil: string;
  agentName: string;
  indicators: Partial<TechnicalIndicator>;
  tags: string[];
  sectorTheme?: string;
}

// ─── Options Chain ─────────────────────────────────────────────────────────────

export interface OptionStrike {
  strikePrice: number;
  expiryDate: string;
  callOI: number;
  callOIChange: number;
  callOIChangePercent: number;
  callVolume: number;
  callIV: number;
  callLTP: number;
  callChange: number;
  callChangePercent: number;
  callBid: number;
  callAsk: number;
  callDelta: number;
  callGamma: number;
  callTheta: number;
  callVega: number;
  putOI: number;
  putOIChange: number;
  putOIChangePercent: number;
  putVolume: number;
  putIV: number;
  putLTP: number;
  putChange: number;
  putChangePercent: number;
  putBid: number;
  putAsk: number;
  putDelta: number;
  putGamma: number;
  putTheta: number;
  putVega: number;
  pcr: number;
  isMaxPain?: boolean;
  isAtTheMoney?: boolean;
}

export interface OptionChain {
  underlyingSymbol: string;
  underlyingPrice: number;
  expiryDate: string;
  expiryDates: string[];
  strikes: OptionStrike[];
  maxPainStrike: number;
  totalCallOI: number;
  totalPutOI: number;
  pcr: number;
  indiaVix: number;
  atmStrike: number;
  timestamp: string;
}

// ─── Dashboard ────────────────────────────────────────────────────────────────

export interface SectorData {
  sector: string;
  change: number;
  volume: number;
  topGainer: string;
  topLoser: string;
}

export interface MoneyFlow {
  date: string;
  fiiNet: number;
  diiNet: number;
  fiiBuy: number;
  fiiSell: number;
  diiBuy: number;
  diiSell: number;
}

export interface GlobalMacroData {
  timestamp: string;
  usIndices: {
    dow: IndexQuote;
    sp500: IndexQuote;
    nasdaq: IndexQuote;
    vix: IndexQuote;
  };
  europeanIndices: {
    ftse: IndexQuote;
    dax: IndexQuote;
    cac: IndexQuote;
  };
  asianIndices: {
    nikkei: IndexQuote;
    hangSeng: IndexQuote;
    sgxNifty: IndexQuote;
    shanghai: IndexQuote;
  };
  currencies: {
    usdInr: number;
    usdInrChange: number;
    dxy: number;
    dxyChange: number;
    eurusd: number;
    gbpusd: number;
    usdjpy: number;
  };
  commodities: {
    gold: CommodityQuote;
    silver: CommodityQuote;
    crude: CommodityQuote;
    naturalGas: CommodityQuote;
    copper: CommodityQuote;
  };
  bonds: {
    us10y: number;
    us10yChange: number;
    us2y: number;
    in10y: number;
    in10yChange: number;
  };
  fedPolicy: 'HAWKISH' | 'NEUTRAL' | 'DOVISH';
  rbiPolicy: 'HAWKISH' | 'NEUTRAL' | 'DOVISH';
}

export interface IndexQuote {
  name: string;
  value: number;
  change: number;
  changePercent: number;
}

export interface CommodityQuote {
  name: string;
  value: number;
  change: number;
  changePercent: number;
  unit: string;
}

export interface MarketDashboard {
  timestamp: string;
  sentimentScore: number;
  indiaVix: number;
  indiaVixChange: number;
  nifty50: MarketData;
  bankNifty: MarketData;
  niftyMidcap: MarketData;
  sensex: MarketData;
  topSignals: TradeSignal[];
  topRisks: string[];
  capitalProtectionStrategy: string;
  sectorHeatmap: SectorData[];
  moneyFlow: MoneyFlow[];
  globalMacro: GlobalMacroData;
  marketStatus: 'PRE_OPEN' | 'OPEN' | 'POST_CLOSE' | 'CLOSED';
  sessionType: 'REGULAR' | 'MUHURAT' | 'SPECIAL';
  tickerData: TickerItem[];
}

export interface TickerItem {
  symbol: string;
  ltp: number;
  change: number;
  changePercent: number;
}

// ─── Alert ───────────────────────────────────────────────────────────────────

export interface Alert {
  id: string;
  type: AlertType;
  severity: AlertSeverity;
  symbol?: string;
  exchange?: Exchange;
  title: string;
  message: string;
  condition?: string;
  targetValue?: number;
  currentValue?: number;
  isTriggered: boolean;
  isRead: boolean;
  isActive: boolean;
  createdAt: string;
  triggeredAt?: string;
  expiresAt?: string;
  notifyEmail: boolean;
  notifyPush: boolean;
  notifySms: boolean;
}

export interface CreateAlertRequest {
  type: AlertType;
  severity: AlertSeverity;
  symbol?: string;
  exchange?: Exchange;
  title: string;
  message: string;
  condition?: string;
  targetValue?: number;
  notifyEmail: boolean;
  notifyPush: boolean;
  notifySms: boolean;
  expiresAt?: string;
}

// ─── Portfolio ────────────────────────────────────────────────────────────────

export interface PortfolioPosition {
  id: string;
  symbol: string;
  exchange: Exchange;
  segment: Segment;
  quantity: number;
  averageCost: number;
  lastTradedPrice: number;
  investedValue: number;
  currentValue: number;
  unrealizedPnL: number;
  unrealizedPnLPercent: number;
  realizedPnL: number;
  dayPnL: number;
  dayPnLPercent: number;
  sector: string;
  addedAt: string;
  tags: string[];
}

export interface PortfolioSummary {
  totalInvested: number;
  totalCurrentValue: number;
  totalUnrealizedPnL: number;
  totalUnrealizedPnLPercent: number;
  totalRealizedPnL: number;
  dayPnL: number;
  dayPnLPercent: number;
  totalPositions: number;
  winningPositions: number;
  losingPositions: number;
  cashBalance: number;
  availableMargin: number;
  usedMargin: number;
}

export interface PerformanceDataPoint {
  date: string;
  portfolioValue: number;
  niftyValue: number;
  portfolioReturn: number;
  niftyReturn: number;
}

// ─── Risk ─────────────────────────────────────────────────────────────────────

export interface RiskMetrics {
  portfolioVaR95: number;
  portfolioVaR99: number;
  expectedShortfall: number;
  maxDrawdown: number;
  currentDrawdown: number;
  sharpeRatio: number;
  beta: number;
  alpha: number;
  volatility: number;
  correlationToNifty: number;
  concentrationRisk: number;
  overnightGapRisk: number;
  globalShockRisk: number;
}

export interface PositionSizeResult {
  quantity: number;
  positionValue: number;
  riskAmount: number;
  rewardAmount: number;
  riskPercent: number;
  rewardPercent: number;
  stopLossAmount: number;
}

// ─── Broker Config ────────────────────────────────────────────────────────────

export interface BrokerConfig {
  broker: BrokerType;
  isEnabled: boolean;
  isConnected: boolean;
  apiKey: string;
  apiSecret: string;
  userId?: string;
  accessToken?: string;
  tokenExpiry?: string;
  paperTrading: boolean;
  autoOrder: boolean;
  maxOrderValue: number;
  lastConnected?: string;
}

// ─── WebSocket Message ────────────────────────────────────────────────────────

export interface WsMessage<T = unknown> {
  type: string;
  payload: T;
  timestamp: string;
}

export interface DrawdownDataPoint {
  date: string;
  drawdown: number;
}
