import React, { useState, useEffect, useCallback } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  Cell,
  Legend,
} from 'recharts';
import { Play, RefreshCw, TrendingUp, TrendingDown, Activity, BarChart2, Search, Trash2 } from 'lucide-react';
import { clsx } from 'clsx';
import toast from 'react-hot-toast';
import { backtestApi } from '@/api/backtest';
import type { BacktestResult, BacktestTrade, EquityCurvePoint, RunBacktestRequest } from '@/api/backtest';
import { formatCurrency, formatPercent, formatDate, getChangeColor } from '@/utils/formatters';

// ─── Constants ────────────────────────────────────────────────────────────────

const STRATEGIES = [
  'EMA_CROSSOVER',
  'RSI_MEAN_REVERSION',
  'MACD_MOMENTUM',
  'BOLLINGER_BAND',
  'VWAP',
];

const STRATEGY_LABELS: Record<string, string> = {
  EMA_CROSSOVER: 'EMA Crossover',
  RSI_MEAN_REVERSION: 'RSI Mean Reversion',
  MACD_MOMENTUM: 'MACD Momentum',
  BOLLINGER_BAND: 'Bollinger Band',
  VWAP: 'VWAP',
};

const INSTRUMENTS = [
  'NIFTY50', 'BANKNIFTY', 'RELIANCE', 'TCS', 'HDFCBANK', 'INFY', 'ICICIBANK',
  'SBIN', 'AXISBANK', 'WIPRO', 'HCLTECH', 'BHARTIARTL', 'ITC', 'LT',
  'SUNPHARMA', 'TATAMOTORS', 'MARUTI', 'GOLD', 'SILVER', 'CRUDEOIL',
];

// ─── StatBox ──────────────────────────────────────────────────────────────────

const StatBox: React.FC<{ label: string; value: string; color?: string }> = ({ label, value, color }) => (
  <div className="bg-bg-tertiary rounded-lg p-3 flex flex-col gap-1 border border-bg-border">
    <span className="text-xs text-gray-500 uppercase tracking-wider">{label}</span>
    <span className={clsx('text-sm font-bold font-mono', color || 'text-white')}>{value}</span>
  </div>
);

// ─── Monthly Returns Heatmap ───────────────────────────────────────────────────

const MonthlyHeatmap: React.FC<{ trades: BacktestTrade[] }> = ({ trades }) => {
  const monthlyReturns = React.useMemo(() => {
    const map: Record<string, number> = {};
    trades.forEach((t) => {
      if (!t.pnl || !t.entryDate) return;
      const key = t.entryDate.slice(0, 7); // YYYY-MM
      map[key] = (map[key] || 0) + t.pnl;
    });
    return Object.entries(map)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([month, pnl]) => ({ month: month.slice(2), pnl }));
  }, [trades]);

  if (monthlyReturns.length === 0) return null;

  const maxAbs = Math.max(...monthlyReturns.map((m) => Math.abs(m.pnl)));

  return (
    <div>
      <div className="text-xs text-gray-500 uppercase tracking-wider mb-2">Monthly P&L Heatmap</div>
      <div className="flex flex-wrap gap-1.5">
        {monthlyReturns.map(({ month, pnl }) => {
          const intensity = maxAbs > 0 ? Math.abs(pnl) / maxAbs : 0;
          const bg = pnl >= 0
            ? `rgba(16, 185, 129, ${0.15 + intensity * 0.7})`
            : `rgba(239, 68, 68, ${0.15 + intensity * 0.7})`;
          return (
            <div
              key={month}
              className="w-12 h-10 rounded flex flex-col items-center justify-center cursor-default"
              style={{ background: bg, border: '1px solid rgba(255,255,255,0.05)' }}
              title={`${month}: ${formatCurrency(pnl, 'INR', true)}`}
            >
              <span className="text-[9px] text-white/70">{month}</span>
              <span className={clsx('text-[10px] font-bold', pnl >= 0 ? 'text-emerald-200' : 'text-red-200')}>
                {pnl >= 0 ? '+' : ''}{(pnl / 1000).toFixed(1)}K
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
};

// ─── Main Backtesting Component ───────────────────────────────────────────────

export const Backtesting: React.FC = () => {
  const [results, setResults] = useState<BacktestResult[]>([]);
  const [selectedResult, setSelectedResult] = useState<BacktestResult | null>(null);
  const [trades, setTrades] = useState<BacktestTrade[]>([]);
  const [equityCurve, setEquityCurve] = useState<EquityCurvePoint[]>([]);
  const [running, setRunning] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [comparing, setComparing] = useState(false);
  const [compareData, setCompareData] = useState<Array<{ name: string; totalReturn: number; sharpeRatio: number; winRate: number }>>([]);
  const [showCompare, setShowCompare] = useState(false);

  // Form state
  const [symbol, setSymbol] = useState('NIFTY50');
  const [strategy, setStrategy] = useState('EMA_CROSSOVER');
  const [startDate, setStartDate] = useState('2023-01-01');
  const [endDate, setEndDate] = useState('2024-12-31');
  const [initialCapital, setInitialCapital] = useState(1000000);
  const [symbolSearch, setSymbolSearch] = useState('');
  const [showSymbolDrop, setShowSymbolDrop] = useState(false);

  const filteredSymbols = INSTRUMENTS.filter((s) =>
    s.toLowerCase().includes(symbolSearch.toLowerCase())
  );

  const fetchResults = useCallback(async () => {
    try {
      const data = await backtestApi.getResults();
      setResults(data);
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    fetchResults();
  }, [fetchResults]);

  const handleRun = async () => {
    const req: RunBacktestRequest = {
      name: `${STRATEGY_LABELS[strategy]} — ${symbol}`,
      symbol,
      strategyName: strategy,
      startDate,
      endDate,
      initialCapital,
    };
    setRunning(true);
    try {
      const result = await backtestApi.runBacktest(req);
      toast.success('Backtest completed!');
      setResults((prev) => [result, ...prev]);
      handleSelectResult(result);
    } catch {
      toast.error('Backtest failed. Check backend logs.');
    } finally {
      setRunning(false);
    }
  };

  const handleSelectResult = useCallback(async (result: BacktestResult) => {
    setSelectedResult(result);
    setLoadingDetail(true);
    try {
      const [t, ec] = await Promise.all([
        backtestApi.getTrades(result.id),
        backtestApi.getEquityCurve(result.id).catch(() => [] as EquityCurvePoint[]),
      ]);
      setTrades(t);
      setEquityCurve(ec);
    } catch {
      toast.error('Failed to load backtest details');
    } finally {
      setLoadingDetail(false);
    }
  }, []);

  const handleDelete = useCallback(async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await backtestApi.deleteResult(id);
      setResults((prev) => prev.filter((r) => r.id !== id));
      if (selectedResult?.id === id) {
        setSelectedResult(null);
        setTrades([]);
        setEquityCurve([]);
      }
      toast.success('Result deleted');
    } catch {
      toast.error('Failed to delete result');
    }
  }, [selectedResult]);

  const handleCompare = useCallback(async () => {
    setComparing(true);
    setShowCompare(true);
    try {
      const promises = STRATEGIES.map((s) =>
        backtestApi.runBacktest({
          name: `Compare: ${s} — ${symbol}`,
          symbol,
          strategyName: s,
          startDate,
          endDate,
          initialCapital,
        }).catch(() => null)
      );
      const allResults = await Promise.all(promises);
      const data = allResults
        .filter(Boolean)
        .map((r) => r!)
        .map((r) => ({
          name: STRATEGY_LABELS[r.strategyName] || r.strategyName,
          totalReturn: r.totalReturn * 100,
          sharpeRatio: r.sharpeRatio,
          winRate: r.winRate * 100,
        }));
      setCompareData(data);
      await fetchResults();
    } catch {
      toast.error('Strategy comparison failed');
    } finally {
      setComparing(false);
    }
  }, [symbol, startDate, endDate, initialCapital, fetchResults]);

  const equityChartData = equityCurve.length > 0
    ? equityCurve
    : trades.reduce((acc: EquityCurvePoint[], t, i) => {
        const prev = acc[i - 1]?.value ?? initialCapital;
        acc.push({ date: t.entryDate, value: prev + (t.pnl ?? 0) });
        return acc;
      }, []);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-lg font-bold text-white">Backtesting</h1>
        <p className="text-xs text-gray-500">Test trading strategies on historical data</p>
      </div>

      {/* Run Backtest Form */}
      <div className="bg-bg-card border border-bg-border rounded-xl p-5">
        <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
          <Play size={14} className="text-accent-blue" />
          Configure Backtest
        </h3>
        <div className="grid grid-cols-2 lg:grid-cols-6 gap-3">
          {/* Instrument */}
          <div className="relative lg:col-span-1">
            <label className="label-form">Instrument</label>
            <input
              type="text"
              value={symbol}
              onChange={(e) => { setSymbol(e.target.value); setSymbolSearch(e.target.value); setShowSymbolDrop(true); }}
              onFocus={() => setShowSymbolDrop(true)}
              placeholder="Symbol"
              className="input-dark w-full"
            />
            {showSymbolDrop && filteredSymbols.length > 0 && (
              <div className="absolute z-10 top-full left-0 right-0 bg-bg-tertiary border border-bg-border rounded-lg shadow-xl max-h-36 overflow-y-auto mt-1">
                {filteredSymbols.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => { setSymbol(s); setSymbolSearch(s); setShowSymbolDrop(false); }}
                    className="w-full text-left px-3 py-1.5 text-xs hover:bg-bg-hover text-gray-300"
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Strategy */}
          <div className="lg:col-span-1">
            <label className="label-form">Strategy</label>
            <select value={strategy} onChange={(e) => setStrategy(e.target.value)} className="input-dark w-full">
              {STRATEGIES.map((s) => (
                <option key={s} value={s}>{STRATEGY_LABELS[s]}</option>
              ))}
            </select>
          </div>

          {/* Start Date */}
          <div>
            <label className="label-form">Start Date</label>
            <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="input-dark w-full" />
          </div>

          {/* End Date */}
          <div>
            <label className="label-form">End Date</label>
            <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="input-dark w-full" />
          </div>

          {/* Capital */}
          <div>
            <label className="label-form">Initial Capital</label>
            <input
              type="number"
              value={initialCapital}
              onChange={(e) => setInitialCapital(Number(e.target.value))}
              className="input-dark w-full"
              step="100000"
            />
          </div>

          {/* Buttons */}
          <div className="flex flex-col gap-2 justify-end">
            <button
              onClick={handleRun}
              disabled={running}
              className="btn-primary flex items-center gap-2 justify-center text-sm"
            >
              {running ? <RefreshCw size={13} className="animate-spin" /> : <Play size={13} />}
              {running ? 'Running...' : 'Run Backtest'}
            </button>
            <button
              onClick={handleCompare}
              disabled={comparing}
              className="btn-secondary flex items-center gap-2 justify-center text-sm"
            >
              {comparing ? <RefreshCw size={13} className="animate-spin" /> : <BarChart2 size={13} />}
              Compare All
            </button>
          </div>
        </div>
      </div>

      {/* Strategy Comparison Chart */}
      {showCompare && compareData.length > 0 && (
        <div className="bg-bg-card border border-bg-border rounded-xl p-5">
          <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
            <BarChart2 size={14} className="text-accent-blue" />
            Strategy Comparison — {symbol}
          </h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={compareData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2d3748" />
              <XAxis dataKey="name" tick={{ fill: '#9ca3af', fontSize: 10 }} />
              <YAxis tick={{ fill: '#6b7280', fontSize: 10 }} unit="%" />
              <Tooltip
                contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', fontSize: 12 }}
                formatter={(val: number) => [`${val.toFixed(2)}%`]}
              />
              <Legend wrapperStyle={{ fontSize: 11 }} />
              <Bar dataKey="totalReturn" name="Total Return %" radius={[3, 3, 0, 0]}>
                {compareData.map((entry, i) => (
                  <Cell key={i} fill={entry.totalReturn >= 0 ? '#10b981' : '#ef4444'} />
                ))}
              </Bar>
              <Bar dataKey="winRate" name="Win Rate %" fill="#3b82f6" radius={[3, 3, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Results Table */}
      <div className="bg-bg-card border border-bg-border rounded-xl overflow-hidden">
        <div className="px-4 py-3 border-b border-bg-border flex items-center justify-between">
          <h3 className="text-sm font-bold text-white">Backtest Results</h3>
          <span className="text-xs text-gray-500">{results.length} runs</span>
        </div>
        <div className="overflow-x-auto">
          <table className="table-dark">
            <thead>
              <tr>
                <th>Name</th>
                <th>Strategy</th>
                <th>Symbol</th>
                <th>Total Ret%</th>
                <th>Ann. Ret%</th>
                <th>Max DD%</th>
                <th>Sharpe</th>
                <th>Win%</th>
                <th>Trades</th>
                <th>Profit Factor</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {results.length === 0 ? (
                <tr>
                  <td colSpan={12} className="text-center text-gray-600 py-8">
                    No backtest results yet. Run your first backtest above.
                  </td>
                </tr>
              ) : (
                results.map((r) => (
                  <tr
                    key={r.id}
                    onClick={() => handleSelectResult(r)}
                    className={clsx(
                      'cursor-pointer',
                      selectedResult?.id === r.id && 'bg-accent-blue/10 border-l-2 border-accent-blue'
                    )}
                  >
                    <td className="font-medium text-white max-w-[150px] truncate">{r.name}</td>
                    <td className="text-gray-400 text-xs">{STRATEGY_LABELS[r.strategyName] || r.strategyName}</td>
                    <td className="font-bold text-accent-blue-light">{r.instrumentSymbol || '—'}</td>
                    <td className={clsx('font-mono font-semibold', getChangeColor(r.totalReturn * 100))}>
                      {formatPercent(r.totalReturn * 100)}
                    </td>
                    <td className={clsx('font-mono', getChangeColor(r.annualizedReturn * 100))}>
                      {formatPercent(r.annualizedReturn * 100)}
                    </td>
                    <td className="font-mono text-red-400">{formatPercent(r.maxDrawdown * 100)}</td>
                    <td className={clsx('font-mono', r.sharpeRatio >= 1 ? 'text-emerald-400' : 'text-yellow-400')}>
                      {r.sharpeRatio?.toFixed(2)}
                    </td>
                    <td className={clsx('font-mono', getChangeColor(r.winRate * 100 - 50))}>
                      {formatPercent(r.winRate * 100)}
                    </td>
                    <td className="font-mono text-gray-300">{r.totalTrades}</td>
                    <td className={clsx('font-mono', r.profitFactor >= 1.5 ? 'text-emerald-400' : r.profitFactor >= 1 ? 'text-yellow-400' : 'text-red-400')}>
                      {r.profitFactor?.toFixed(2)}
                    </td>
                    <td>
                      <span className={clsx(
                        'text-xs px-2 py-0.5 rounded font-semibold',
                        r.status === 'COMPLETED' ? 'bg-emerald-500/20 text-emerald-400' :
                        r.status === 'RUNNING' ? 'bg-yellow-500/20 text-yellow-400' :
                        r.status === 'FAILED' ? 'bg-red-500/20 text-red-400' :
                        'bg-gray-600/30 text-gray-400'
                      )}>
                        {r.status}
                      </span>
                    </td>
                    <td>
                      <button
                        onClick={(e) => handleDelete(r.id, e)}
                        className="text-gray-600 hover:text-red-400 p-1 rounded transition-colors"
                      >
                        <Trash2 size={13} />
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Detail Panel */}
      {selectedResult && (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-bold text-white">{selectedResult.name}</h3>
            {loadingDetail && <RefreshCw size={13} className="text-gray-500 animate-spin" />}
          </div>

          {/* Key Stats */}
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-2">
            <StatBox label="Total Return" value={formatPercent(selectedResult.totalReturn * 100)} color={getChangeColor(selectedResult.totalReturn)} />
            <StatBox label="Ann. Return" value={formatPercent(selectedResult.annualizedReturn * 100)} color={getChangeColor(selectedResult.annualizedReturn)} />
            <StatBox label="Max Drawdown" value={formatPercent(selectedResult.maxDrawdown * 100)} color="text-red-400" />
            <StatBox label="Sharpe Ratio" value={selectedResult.sharpeRatio?.toFixed(2)} color={selectedResult.sharpeRatio >= 1 ? 'text-emerald-400' : 'text-yellow-400'} />
            <StatBox label="Win Rate" value={formatPercent(selectedResult.winRate * 100)} color={getChangeColor(selectedResult.winRate * 100 - 50)} />
            <StatBox label="Total Trades" value={String(selectedResult.totalTrades)} />
            <StatBox label="Profit Factor" value={selectedResult.profitFactor?.toFixed(2)} color={selectedResult.profitFactor >= 1.5 ? 'text-emerald-400' : 'text-yellow-400'} />
            <StatBox label="Commission" value={formatCurrency(selectedResult.commissionPaid ?? 0, 'INR', true)} color="text-gray-400" />
          </div>

          {/* Charts Row */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            {/* Equity Curve */}
            <div className="bg-bg-card border border-bg-border rounded-xl p-4">
              <h4 className="text-xs font-semibold text-gray-400 uppercase mb-3">Equity Curve</h4>
              {equityChartData.length > 0 ? (
                <ResponsiveContainer width="100%" height={200}>
                  <LineChart data={equityChartData} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#2d3748" />
                    <XAxis dataKey="date" tick={{ fill: '#6b7280', fontSize: 9 }} tickFormatter={(v) => v?.slice(2, 10)} />
                    <YAxis tick={{ fill: '#6b7280', fontSize: 10 }} tickFormatter={(v) => formatCurrency(v, 'INR', true)} />
                    <Tooltip
                      contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', fontSize: 12 }}
                      formatter={(val: number) => [formatCurrency(val, 'INR', true), 'Portfolio']}
                    />
                    <Line type="monotone" dataKey="value" stroke="#3b82f6" strokeWidth={2} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <div className="flex items-center justify-center h-[200px] text-gray-600 text-sm">
                  No equity curve data
                </div>
              )}
            </div>

            {/* Monthly Heatmap */}
            <div className="bg-bg-card border border-bg-border rounded-xl p-4">
              <h4 className="text-xs font-semibold text-gray-400 uppercase mb-3">Monthly Returns</h4>
              {trades.length > 0 ? (
                <MonthlyHeatmap trades={trades} />
              ) : (
                <div className="flex items-center justify-center h-[200px] text-gray-600 text-sm">
                  No trade data
                </div>
              )}
            </div>
          </div>

          {/* Trade List */}
          <div className="bg-bg-card border border-bg-border rounded-xl overflow-hidden">
            <div className="px-4 py-3 border-b border-bg-border">
              <h4 className="text-sm font-bold text-white">Individual Trades ({trades.length})</h4>
            </div>
            <div className="overflow-x-auto max-h-80">
              <table className="table-dark">
                <thead className="sticky top-0 bg-bg-card">
                  <tr>
                    <th>Entry Date</th>
                    <th>Exit Date</th>
                    <th>Dir</th>
                    <th>Qty</th>
                    <th>Entry</th>
                    <th>Exit</th>
                    <th>P&L</th>
                    <th>P&L%</th>
                    <th>Days</th>
                    <th>Exit Reason</th>
                  </tr>
                </thead>
                <tbody>
                  {trades.length === 0 ? (
                    <tr>
                      <td colSpan={10} className="text-center text-gray-600 py-6">No trades recorded</td>
                    </tr>
                  ) : (
                    trades.map((t) => (
                      <tr key={t.id}>
                        <td className="text-xs font-mono">{formatDate(t.entryDate)}</td>
                        <td className="text-xs font-mono">{t.exitDate ? formatDate(t.exitDate) : '—'}</td>
                        <td>
                          <span className={t.direction === 'LONG' ? 'badge-buy' : 'badge-sell'}>
                            {t.direction}
                          </span>
                        </td>
                        <td className="font-mono">{t.quantity}</td>
                        <td className="font-mono">{formatCurrency(t.entryPrice)}</td>
                        <td className="font-mono">{t.exitPrice ? formatCurrency(t.exitPrice) : '—'}</td>
                        <td className={clsx('font-mono font-semibold', getChangeColor(t.pnl ?? 0))}>
                          {t.pnl != null ? formatCurrency(t.pnl, 'INR', true) : '—'}
                        </td>
                        <td className={clsx('font-mono text-xs', getChangeColor(t.pnlPercent ?? 0))}>
                          {t.pnlPercent != null ? formatPercent(t.pnlPercent) : '—'}
                        </td>
                        <td className="font-mono text-gray-400">{t.holdingDays ?? '—'}</td>
                        <td className="text-xs text-gray-400">{t.exitReason ?? '—'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Backtesting;
