import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { Play, X, RefreshCw, RotateCcw, DollarSign, TrendingUp, Activity, AlertCircle } from 'lucide-react';
import { clsx } from 'clsx';
import { Client } from '@stomp/stompjs';
import toast from 'react-hot-toast';
import { paperApi } from '@/api/paper';
import type { PaperTrade, PaperPortfolio, PaperPerformancePoint, OpenPaperTradeRequest } from '@/api/paper';
import { signalsApi } from '@/api/signals';
import type { TradeSignal } from '@/types';
import { formatCurrency, formatPercent, getChangeColor, formatDateTime, getSignalBgColor } from '@/utils/formatters';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

const INSTRUMENTS = [
  'NIFTY50', 'BANKNIFTY', 'RELIANCE', 'TCS', 'HDFCBANK', 'INFY', 'ICICIBANK',
  'SBIN', 'AXISBANK', 'WIPRO', 'HCLTECH', 'BHARTIARTL', 'ITC', 'LT',
  'SUNPHARMA', 'TATAMOTORS', 'MARUTI', 'GOLD', 'SILVER', 'CRUDEOIL',
];

// ─── Signal Card (mini) ───────────────────────────────────────────────────────

interface SignalCardMiniProps {
  signal: TradeSignal;
  onPaperTrade: (signal: TradeSignal) => void;
}

const SignalCardMini: React.FC<SignalCardMiniProps> = ({ signal, onPaperTrade }) => {
  const isBuy = signal.signalType === 'STRONG_BUY' || signal.signalType === 'BUY';
  return (
    <div className={clsx('rounded-lg border p-3 space-y-1.5', getSignalBgColor(signal.signalType))}>
      <div className="flex items-start justify-between">
        <div>
          <div className="font-bold text-white text-sm">{signal.symbol}</div>
          <div className="text-xs text-gray-400">{signal.exchange} · {signal.timeHorizon}</div>
        </div>
        <span className={clsx(
          'text-xs font-bold px-2 py-0.5 rounded',
          isBuy ? 'bg-emerald-500/30 text-emerald-300' : 'bg-red-500/30 text-red-300'
        )}>
          {signal.signalType.replace('_', ' ')}
        </span>
      </div>
      <div className="grid grid-cols-3 gap-1 text-xs">
        <div><span className="text-gray-500">Entry</span><div className="font-mono text-white">{signal.entryPrice?.toFixed(2)}</div></div>
        <div><span className="text-gray-500">SL</span><div className="font-mono text-red-400">{signal.stopLoss?.toFixed(2)}</div></div>
        <div><span className="text-gray-500">T1</span><div className="font-mono text-emerald-400">{signal.target1?.toFixed(2)}</div></div>
      </div>
      <div className="flex items-center justify-between">
        <span className="text-xs text-gray-400">Confidence: <span className="text-white">{signal.confidenceScore?.toFixed(0)}%</span></span>
        <button
          onClick={() => onPaperTrade(signal)}
          className="text-xs bg-accent-blue/20 hover:bg-accent-blue/40 text-accent-blue-light border border-accent-blue/30 px-2 py-1 rounded transition-colors"
        >
          Paper Trade
        </button>
      </div>
    </div>
  );
};

// ─── Open Position Row ────────────────────────────────────────────────────────

interface OpenPositionRowProps {
  trade: PaperTrade;
  onClose: (id: number) => void;
}

const OpenPositionRow: React.FC<OpenPositionRowProps> = ({ trade, onClose }) => {
  const ltp = trade.ltp ?? trade.entryPrice;
  const unrealPnl = trade.direction === 'LONG'
    ? (ltp - trade.entryPrice) * trade.quantity
    : (trade.entryPrice - ltp) * trade.quantity;
  const unrealPct = (unrealPnl / (trade.entryPrice * trade.quantity)) * 100;

  return (
    <div className="flex items-center gap-3 px-4 py-3 border-b border-bg-border/50 hover:bg-bg-hover transition-colors">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="font-bold text-white text-sm">{trade.symbol}</span>
          <span className={trade.direction === 'LONG' ? 'badge-buy' : 'badge-sell'}>{trade.direction}</span>
        </div>
        <div className="text-xs text-gray-500 mt-0.5">
          {trade.quantity} @ {formatCurrency(trade.entryPrice)} · LTP: {formatCurrency(ltp)}
        </div>
      </div>
      <div className="text-right shrink-0">
        <div className={clsx('font-mono font-bold text-sm', getChangeColor(unrealPnl))}>
          {formatCurrency(unrealPnl, 'INR', true)}
        </div>
        <div className={clsx('text-xs font-mono', getChangeColor(unrealPct))}>
          {formatPercent(unrealPct)}
        </div>
      </div>
      <button
        onClick={() => onClose(trade.id)}
        className="text-gray-600 hover:text-red-400 p-1 rounded shrink-0 transition-colors"
        title="Close at market"
      >
        <X size={14} />
      </button>
    </div>
  );
};

// ─── Manual Trade Form ────────────────────────────────────────────────────────

interface ManualTradeFormProps {
  onSubmit: (req: OpenPaperTradeRequest) => Promise<boolean>;
  prefill?: Partial<OpenPaperTradeRequest>;
  onClearPrefill: () => void;
}

const ManualTradeForm: React.FC<ManualTradeFormProps> = ({ onSubmit, prefill, onClearPrefill }) => {
  const [form, setForm] = useState<OpenPaperTradeRequest>({
    symbol: '',
    direction: 'LONG',
    quantity: 1,
    entryPrice: 0,
    stoploss: undefined,
    target: undefined,
    notes: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [symSearch, setSymSearch] = useState('');
  const [showDrop, setShowDrop] = useState(false);

  useEffect(() => {
    if (prefill) {
      setForm((f) => ({ ...f, ...prefill }));
      setSymSearch(prefill.symbol || '');
    }
  }, [prefill]);

  const filtered = INSTRUMENTS.filter((s) => s.toLowerCase().includes(symSearch.toLowerCase()));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.symbol) { toast.error('Select an instrument'); return; }
    if (!form.entryPrice || form.entryPrice <= 0) { toast.error('Enter a valid entry price'); return; }
    if (!form.quantity || form.quantity < 1) { toast.error('Quantity must be at least 1'); return; }
    setSubmitting(true);
    const ok = await onSubmit(form);
    if (ok) {
      setForm({ symbol: '', direction: 'LONG', quantity: 1, entryPrice: 0 });
      setSymSearch('');
      onClearPrefill();
    }
    setSubmitting(false);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      {prefill && (
        <div className="bg-accent-blue/10 border border-accent-blue/30 rounded-lg px-3 py-2 flex items-center justify-between">
          <span className="text-xs text-accent-blue-light">Pre-filled from signal: {prefill.symbol}</span>
          <button type="button" onClick={onClearPrefill} className="text-gray-500 hover:text-white">
            <X size={12} />
          </button>
        </div>
      )}

      {/* Symbol */}
      <div className="relative">
        <label className="label-form">Instrument</label>
        <input
          type="text"
          value={form.symbol || symSearch}
          onChange={(e) => { setSymSearch(e.target.value); setForm((f) => ({ ...f, symbol: e.target.value })); setShowDrop(true); }}
          onFocus={() => setShowDrop(true)}
          placeholder="Search symbol..."
          className="input-dark w-full"
        />
        {showDrop && filtered.length > 0 && (
          <div className="absolute z-10 top-full left-0 right-0 bg-bg-tertiary border border-bg-border rounded-lg shadow-xl max-h-32 overflow-y-auto mt-1">
            {filtered.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => { setForm((f) => ({ ...f, symbol: s })); setSymSearch(s); setShowDrop(false); }}
                className="w-full text-left px-3 py-1.5 text-xs hover:bg-bg-hover text-gray-300"
              >
                {s}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Direction */}
      <div className="grid grid-cols-2 gap-2">
        {(['LONG', 'SHORT'] as const).map((dir) => (
          <button
            key={dir}
            type="button"
            onClick={() => setForm((f) => ({ ...f, direction: dir }))}
            className={clsx(
              'py-2 rounded-lg text-sm font-bold transition-colors border',
              form.direction === dir
                ? dir === 'LONG'
                  ? 'bg-emerald-500/30 text-emerald-300 border-emerald-500/50'
                  : 'bg-red-500/30 text-red-300 border-red-500/50'
                : 'bg-bg-tertiary text-gray-400 border-bg-border hover:text-gray-200'
            )}
          >
            {dir === 'LONG' ? 'LONG (Buy)' : 'SHORT (Sell)'}
          </button>
        ))}
      </div>

      {/* Qty + Entry */}
      <div className="grid grid-cols-2 gap-2">
        <div>
          <label className="label-form">Quantity</label>
          <input
            type="number"
            value={form.quantity}
            onChange={(e) => setForm((f) => ({ ...f, quantity: Number(e.target.value) }))}
            min={1}
            className="input-dark w-full"
          />
        </div>
        <div>
          <label className="label-form">Entry Price</label>
          <input
            type="number"
            value={form.entryPrice || ''}
            onChange={(e) => setForm((f) => ({ ...f, entryPrice: parseFloat(e.target.value) || 0 }))}
            step="0.05"
            placeholder="0.00"
            className="input-dark w-full"
          />
        </div>
      </div>

      {/* SL + Target */}
      <div className="grid grid-cols-2 gap-2">
        <div>
          <label className="label-form">Stop Loss</label>
          <input
            type="number"
            value={form.stoploss || ''}
            onChange={(e) => setForm((f) => ({ ...f, stoploss: parseFloat(e.target.value) || undefined }))}
            step="0.05"
            placeholder="Optional"
            className="input-dark w-full"
          />
        </div>
        <div>
          <label className="label-form">Target</label>
          <input
            type="number"
            value={form.target || ''}
            onChange={(e) => setForm((f) => ({ ...f, target: parseFloat(e.target.value) || undefined }))}
            step="0.05"
            placeholder="Optional"
            className="input-dark w-full"
          />
        </div>
      </div>

      <div>
        <label className="label-form">Notes</label>
        <input
          type="text"
          value={form.notes || ''}
          onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
          placeholder="Optional trade notes"
          className="input-dark w-full"
        />
      </div>

      <button type="submit" disabled={submitting} className="btn-primary w-full flex items-center justify-center gap-2">
        {submitting ? <RefreshCw size={13} className="animate-spin" /> : <Play size={13} />}
        Place Paper Trade
      </button>
    </form>
  );
};

// ─── Main PaperTrading Component ──────────────────────────────────────────────

export const PaperTrading: React.FC = () => {
  const [portfolio, setPortfolio] = useState<PaperPortfolio | null>(null);
  const [performance, setPerformance] = useState<PaperPerformancePoint[]>([]);
  const [signals, setSignals] = useState<TradeSignal[]>([]);
  const [loading, setLoading] = useState(false);
  const [prefill, setPrefill] = useState<Partial<OpenPaperTradeRequest> | undefined>();
  const [resetConfirm, setResetConfirm] = useState(false);
  const stompRef = useRef<Client | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [port, perf, sigs] = await Promise.all([
        paperApi.getPortfolio(),
        paperApi.getPerformance(30),
        signalsApi.getSignals({ pageSize: 10 }).then((r) => r.signals).catch(() => [] as TradeSignal[]),
      ]);
      setPortfolio(port);
      setPerformance(perf);
      setSignals(sigs);
    } catch {
      toast.error('Failed to load paper trading data');
    } finally {
      setLoading(false);
    }
  }, []);

  // WebSocket for real-time P&L updates
  useEffect(() => {
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/paper-trades', (msg) => {
          try {
            const updated: PaperTrade = JSON.parse(msg.body);
            setPortfolio((prev) => {
              if (!prev) return prev;
              const openTrades = prev.openTrades.map((t) =>
                t.id === updated.id ? { ...t, ...updated } : t
              );
              const totalPnl = openTrades.reduce((sum, t) => {
                const ltp = t.ltp ?? t.entryPrice;
                const pnl = t.direction === 'LONG'
                  ? (ltp - t.entryPrice) * t.quantity
                  : (t.entryPrice - ltp) * t.quantity;
                return sum + pnl;
              }, 0);
              return { ...prev, openTrades, totalPnl };
            });
          } catch (_) {}
        });
      },
    });
    client.activate();
    stompRef.current = client;
    return () => { client.deactivate(); };
  }, []);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 15000);
    return () => clearInterval(interval);
  }, [fetchData]);

  const handleOpenTrade = useCallback(async (req: OpenPaperTradeRequest): Promise<boolean> => {
    try {
      await paperApi.openTrade(req);
      toast.success(`Opened ${req.direction} ${req.symbol}`);
      await fetchData();
      return true;
    } catch {
      toast.error('Failed to open trade');
      return false;
    }
  }, [fetchData]);

  const handleCloseTrade = useCallback(async (id: number) => {
    const trade = portfolio?.openTrades.find((t) => t.id === id);
    if (!trade) return;
    const exitPrice = trade.ltp ?? trade.entryPrice;
    try {
      await paperApi.closeTrade(id, { exitPrice, exitReason: 'MANUAL' });
      toast.success(`Closed ${trade.symbol} at ${formatCurrency(exitPrice)}`);
      await fetchData();
    } catch {
      toast.error('Failed to close trade');
    }
  }, [portfolio, fetchData]);

  const handlePaperTradeFromSignal = useCallback((signal: TradeSignal) => {
    const isBuy = signal.signalType === 'STRONG_BUY' || signal.signalType === 'BUY';
    setPrefill({
      symbol: signal.symbol,
      signalId: signal.id ? Number(signal.id) : undefined,
      direction: isBuy ? 'LONG' : 'SHORT',
      entryPrice: signal.entryPrice,
      stoploss: signal.stopLoss,
      target: signal.target1,
      quantity: 1,
    });
  }, []);

  const handleReset = async () => {
    try {
      await paperApi.resetPortfolio();
      toast.success('Portfolio reset');
      setResetConfirm(false);
      fetchData();
    } catch {
      toast.error('Reset failed');
    }
  };

  const openTrades = portfolio?.openTrades ?? [];

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-white">Paper Trading</h1>
          <p className="text-xs text-gray-500">Risk-free strategy testing with real market data</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={fetchData} disabled={loading} className="btn-secondary flex items-center gap-2 text-sm">
            <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
            Refresh
          </button>
          {!resetConfirm ? (
            <button onClick={() => setResetConfirm(true)} className="btn-secondary flex items-center gap-2 text-sm text-red-400 border-red-500/30 hover:bg-red-500/10">
              <RotateCcw size={13} />
              Reset
            </button>
          ) : (
            <div className="flex gap-2">
              <button onClick={() => setResetConfirm(false)} className="btn-secondary text-sm">Cancel</button>
              <button onClick={handleReset} className="btn-danger text-sm">Confirm Reset</button>
            </div>
          )}
        </div>
      </div>

      {/* Summary Row */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
        {[
          { label: 'Total Capital', value: formatCurrency(portfolio?.totalCapital ?? 0, 'INR', true), icon: <DollarSign size={14} />, color: 'text-white' },
          { label: 'Available', value: formatCurrency(portfolio?.availableCapital ?? 0, 'INR', true), icon: <DollarSign size={14} />, color: 'text-accent-blue-light' },
          { label: 'Open P&L', value: formatCurrency(portfolio?.totalPnl ?? 0, 'INR', true), icon: <TrendingUp size={14} />, color: getChangeColor(portfolio?.totalPnl ?? 0) },
          { label: 'Open Positions', value: String(openTrades.length), icon: <Activity size={14} />, color: 'text-white' },
          { label: 'Win Rate', value: `${((portfolio?.winRate ?? 0) * 100).toFixed(1)}%`, icon: <Activity size={14} />, color: 'text-emerald-400' },
        ].map((s) => (
          <div key={s.label} className="bg-bg-card border border-bg-border rounded-xl p-3 flex flex-col gap-1">
            <div className="flex items-center justify-between">
              <span className="text-xs text-gray-500 uppercase tracking-wider">{s.label}</span>
              <span className="text-gray-600">{s.icon}</span>
            </div>
            <span className={clsx('font-bold font-mono text-base', s.color)}>{s.value}</span>
          </div>
        ))}
      </div>

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Left: Signals + Manual Trade */}
        <div className="space-y-4">
          {/* Manual Trade Form */}
          <div className="bg-bg-card border border-bg-border rounded-xl p-4">
            <h3 className="text-sm font-bold text-white mb-3 flex items-center gap-2">
              <Play size={13} className="text-accent-blue" />
              New Manual Trade
            </h3>
            <ManualTradeForm
              onSubmit={handleOpenTrade}
              prefill={prefill}
              onClearPrefill={() => setPrefill(undefined)}
            />
          </div>

          {/* Signal List */}
          <div className="bg-bg-card border border-bg-border rounded-xl overflow-hidden">
            <div className="px-4 py-3 border-b border-bg-border">
              <h3 className="text-sm font-bold text-white">Live Signals</h3>
              <p className="text-xs text-gray-500">Click "Paper Trade" to auto-fill form</p>
            </div>
            <div className="p-3 space-y-2 max-h-80 overflow-y-auto">
              {signals.length === 0 ? (
                <div className="flex items-center justify-center h-20 text-gray-600 text-xs">
                  No active signals
                </div>
              ) : (
                signals.slice(0, 6).map((s) => (
                  <SignalCardMini
                    key={s.id}
                    signal={s}
                    onPaperTrade={handlePaperTradeFromSignal}
                  />
                ))
              )}
            </div>
          </div>
        </div>

        {/* Right: Portfolio */}
        <div className="lg:col-span-2 space-y-4">
          {/* Performance Chart */}
          <div className="bg-bg-card border border-bg-border rounded-xl p-4">
            <h3 className="text-sm font-bold text-white mb-3">Portfolio Equity Curve</h3>
            {performance.length > 0 ? (
              <ResponsiveContainer width="100%" height={180}>
                <LineChart data={performance} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#2d3748" />
                  <XAxis dataKey="date" tick={{ fill: '#6b7280', fontSize: 10 }} tickFormatter={(v) => v?.slice(5)} />
                  <YAxis tick={{ fill: '#6b7280', fontSize: 10 }} tickFormatter={(v) => formatCurrency(v, 'INR', true)} />
                  <Tooltip
                    contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', fontSize: 12 }}
                    formatter={(val: number) => [formatCurrency(val, 'INR', true), 'Portfolio Value']}
                  />
                  <Line type="monotone" dataKey="portfolioValue" stroke="#3b82f6" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex flex-col items-center justify-center h-[180px] gap-2 text-gray-600">
                <AlertCircle size={20} />
                <span className="text-sm">Open your first paper trade to see performance</span>
              </div>
            )}
          </div>

          {/* Open Positions */}
          <div className="bg-bg-card border border-bg-border rounded-xl overflow-hidden">
            <div className="px-4 py-3 border-b border-bg-border flex items-center justify-between">
              <h3 className="text-sm font-bold text-white">Open Positions ({openTrades.length})</h3>
              {portfolio && (
                <span className={clsx('text-sm font-mono font-bold', getChangeColor(portfolio.totalPnl))}>
                  {formatCurrency(portfolio.totalPnl, 'INR', true)}
                </span>
              )}
            </div>
            <div className="max-h-64 overflow-y-auto">
              {openTrades.length === 0 ? (
                <div className="flex items-center justify-center h-20 text-gray-600 text-sm">
                  No open positions
                </div>
              ) : (
                openTrades.map((t) => (
                  <OpenPositionRow key={t.id} trade={t} onClose={handleCloseTrade} />
                ))
              )}
            </div>
          </div>

          {/* Recent Closed Trades */}
          {(portfolio?.closedTrades ?? []).length > 0 && (
            <div className="bg-bg-card border border-bg-border rounded-xl overflow-hidden">
              <div className="px-4 py-3 border-b border-bg-border">
                <h3 className="text-sm font-bold text-white">Recent Closed Trades</h3>
              </div>
              <div className="overflow-x-auto">
                <table className="table-dark">
                  <thead>
                    <tr>
                      <th>Symbol</th>
                      <th>Dir</th>
                      <th>P&L</th>
                      <th>P&L%</th>
                      <th>Exit Reason</th>
                      <th>Closed At</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(portfolio?.closedTrades ?? []).slice(0, 10).map((t) => (
                      <tr key={t.id}>
                        <td className="font-bold text-white">{t.symbol}</td>
                        <td>
                          <span className={t.direction === 'LONG' ? 'badge-buy' : 'badge-sell'}>
                            {t.direction}
                          </span>
                        </td>
                        <td className={clsx('font-mono font-semibold', getChangeColor(t.pnl ?? 0))}>
                          {t.pnl != null ? formatCurrency(t.pnl, 'INR', true) : '—'}
                        </td>
                        <td className={clsx('font-mono text-xs', getChangeColor(t.pnlPercent ?? 0))}>
                          {t.pnlPercent != null ? formatPercent(t.pnlPercent) : '—'}
                        </td>
                        <td className="text-xs text-gray-400">{t.exitReason ?? 'MANUAL'}</td>
                        <td className="text-xs text-gray-500">
                          {t.exitTime ? formatDateTime(t.exitTime) : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default PaperTrading;
