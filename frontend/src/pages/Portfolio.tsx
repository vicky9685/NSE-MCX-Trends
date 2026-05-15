import React, { useState, useEffect, useCallback } from 'react';
import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Legend,
} from 'recharts';
import { RefreshCw, X, TrendingUp, TrendingDown, DollarSign, Activity } from 'lucide-react';
import { clsx } from 'clsx';
import toast from 'react-hot-toast';
import { paperApi } from '@/api/paper';
import type { PaperPortfolio, PaperPerformancePoint, PaperTrade } from '@/api/paper';
import { formatCurrency, formatPercent, getChangeColor, formatDateTime } from '@/utils/formatters';

// ─── Sector colors for donut chart ────────────────────────────────────────────

const SECTOR_COLORS = [
  '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6',
  '#06b6d4', '#84cc16', '#f97316', '#ec4899', '#14b8a6',
  '#a78bfa', '#fb923c', '#34d399', '#60a5fa', '#fbbf24',
];

// ─── StatCard ─────────────────────────────────────────────────────────────────

interface StatCardProps {
  label: string;
  value: string;
  sub?: string;
  icon: React.ReactNode;
  valueColor?: string;
}

const StatCard: React.FC<StatCardProps> = ({ label, value, sub, icon, valueColor }) => (
  <div className="bg-bg-card border border-bg-border rounded-xl p-4 flex flex-col gap-2">
    <div className="flex items-center justify-between">
      <span className="text-xs text-gray-500 uppercase tracking-wider font-semibold">{label}</span>
      <span className="text-gray-600">{icon}</span>
    </div>
    <span className={clsx('text-xl font-bold font-mono', valueColor || 'text-white')}>{value}</span>
    {sub && <span className="text-xs text-gray-500">{sub}</span>}
  </div>
);

// ─── CloseModal ───────────────────────────────────────────────────────────────

interface CloseModalProps {
  trade: PaperTrade;
  onClose: () => void;
  onConfirm: (exitPrice: number) => void;
}

const CloseModal: React.FC<CloseModalProps> = ({ trade, onClose, onConfirm }) => {
  const [exitPrice, setExitPrice] = useState(trade.ltp ?? trade.entryPrice);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="bg-bg-secondary border border-bg-border rounded-xl p-6 w-96 shadow-xl">
        <div className="flex justify-between items-center mb-4">
          <h3 className="font-bold text-white">Close Position</h3>
          <button onClick={onClose} className="text-gray-500 hover:text-white">
            <X size={18} />
          </button>
        </div>
        <div className="space-y-3 mb-5">
          <div className="flex justify-between text-sm">
            <span className="text-gray-400">Symbol</span>
            <span className="font-bold text-white">{trade.symbol}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-gray-400">Direction</span>
            <span className={trade.direction === 'LONG' ? 'text-emerald-400' : 'text-red-400'}>
              {trade.direction}
            </span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-gray-400">Qty</span>
            <span className="text-white">{trade.quantity}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-gray-400">Entry Price</span>
            <span className="text-white font-mono">{formatCurrency(trade.entryPrice)}</span>
          </div>
          <div>
            <label className="label-form">Exit Price</label>
            <input
              type="number"
              value={exitPrice}
              onChange={(e) => setExitPrice(parseFloat(e.target.value) || 0)}
              step="0.05"
              className="input-dark w-full"
            />
          </div>
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="btn-secondary flex-1">Cancel</button>
          <button onClick={() => onConfirm(exitPrice)} className="btn-primary flex-1">
            Close Trade
          </button>
        </div>
      </div>
    </div>
  );
};

// ─── Main Portfolio Component ─────────────────────────────────────────────────

export const Portfolio: React.FC = () => {
  const [portfolio, setPortfolio] = useState<PaperPortfolio | null>(null);
  const [performance, setPerformance] = useState<PaperPerformancePoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [closingTrade, setClosingTrade] = useState<PaperTrade | null>(null);
  const [activeTab, setActiveTab] = useState<'open' | 'closed'>('open');

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      const [port, perf] = await Promise.all([
        paperApi.getPortfolio(),
        paperApi.getPerformance(60),
      ]);
      setPortfolio(port);
      setPerformance(perf);
    } catch {
      toast.error('Failed to load portfolio data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 15000);
    return () => clearInterval(interval);
  }, [fetchData]);

  const handleCloseTrade = useCallback(async (exitPrice: number) => {
    if (!closingTrade) return;
    try {
      await paperApi.closeTrade(closingTrade.id, { exitPrice, exitReason: 'MANUAL' });
      toast.success(`Closed ${closingTrade.symbol} at ${formatCurrency(exitPrice)}`);
      setClosingTrade(null);
      fetchData();
    } catch {
      toast.error('Failed to close trade');
    }
  }, [closingTrade, fetchData]);

  // Sector allocation data from open trades
  const sectorData = React.useMemo(() => {
    if (!portfolio?.openTrades?.length) return [];
    const map: Record<string, number> = {};
    portfolio.openTrades.forEach((t) => {
      const sector = t.symbol.substring(0, 4);
      map[sector] = (map[sector] || 0) + (t.entryPrice * t.quantity);
    });
    return Object.entries(map).map(([name, value]) => ({ name, value }));
  }, [portfolio]);

  const openTrades = portfolio?.openTrades ?? [];
  const closedTrades = portfolio?.closedTrades ?? [];

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-white">Paper Trading Portfolio</h1>
          <p className="text-xs text-gray-500">Simulated positions and P&L tracker</p>
        </div>
        <button
          onClick={fetchData}
          disabled={loading}
          className="btn-secondary flex items-center gap-2 text-sm"
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
        <StatCard
          label="Total Capital"
          value={formatCurrency(portfolio?.totalCapital ?? 0, 'INR', true)}
          icon={<DollarSign size={16} />}
        />
        <StatCard
          label="Deployed"
          value={formatCurrency(portfolio?.deployedCapital ?? 0, 'INR', true)}
          sub={`${(((portfolio?.deployedCapital ?? 0) / (portfolio?.totalCapital || 1)) * 100).toFixed(1)}% deployed`}
          icon={<Activity size={16} />}
        />
        <StatCard
          label="Available"
          value={formatCurrency(portfolio?.availableCapital ?? 0, 'INR', true)}
          icon={<DollarSign size={16} />}
          valueColor="text-accent-blue-light"
        />
        <StatCard
          label="Total P&L"
          value={formatCurrency(portfolio?.totalPnl ?? 0, 'INR', true)}
          sub={formatPercent(portfolio?.totalPnlPercent ?? 0)}
          icon={<TrendingUp size={16} />}
          valueColor={getChangeColor(portfolio?.totalPnl ?? 0)}
        />
        <StatCard
          label="Win Rate"
          value={`${((portfolio?.winRate ?? 0) * 100).toFixed(1)}%`}
          sub={`${portfolio?.winningTrades ?? 0}W / ${portfolio?.losingTrades ?? 0}L`}
          icon={<Activity size={16} />}
          valueColor="text-emerald-400"
        />
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Performance Chart */}
        <div className="lg:col-span-2 bg-bg-card border border-bg-border rounded-xl p-4">
          <h3 className="text-sm font-semibold text-gray-300 mb-4">Portfolio vs Nifty Performance</h3>
          {performance.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <LineChart data={performance} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#2d3748" />
                <XAxis
                  dataKey="date"
                  tick={{ fill: '#6b7280', fontSize: 10 }}
                  tickFormatter={(v) => v.slice(5)}
                />
                <YAxis tick={{ fill: '#6b7280', fontSize: 10 }} tickFormatter={(v) => `${v.toFixed(1)}%`} />
                <Tooltip
                  contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', fontSize: 12 }}
                  formatter={(val: number) => [`${val.toFixed(2)}%`]}
                />
                <Legend wrapperStyle={{ fontSize: 11, color: '#9ca3af' }} />
                <Line
                  type="monotone"
                  dataKey="pnlPercent"
                  name="Portfolio"
                  stroke="#3b82f6"
                  strokeWidth={2}
                  dot={false}
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-[220px] text-gray-600 text-sm">
              No performance data yet
            </div>
          )}
        </div>

        {/* Sector Allocation */}
        <div className="bg-bg-card border border-bg-border rounded-xl p-4">
          <h3 className="text-sm font-semibold text-gray-300 mb-4">Sector Allocation</h3>
          {sectorData.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={sectorData}
                  cx="50%"
                  cy="50%"
                  innerRadius={55}
                  outerRadius={85}
                  paddingAngle={2}
                  dataKey="value"
                >
                  {sectorData.map((_, i) => (
                    <Cell key={i} fill={SECTOR_COLORS[i % SECTOR_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', fontSize: 12 }}
                  formatter={(val: number) => [formatCurrency(val, 'INR', true)]}
                />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-[220px] text-gray-600 text-sm">
              No open positions
            </div>
          )}
          <div className="flex flex-wrap gap-1.5 mt-2">
            {sectorData.slice(0, 8).map((s, i) => (
              <span key={s.name} className="flex items-center gap-1 text-xs text-gray-400">
                <span
                  className="w-2 h-2 rounded-full"
                  style={{ background: SECTOR_COLORS[i % SECTOR_COLORS.length] }}
                />
                {s.name}
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* Trades Table */}
      <div className="bg-bg-card border border-bg-border rounded-xl overflow-hidden">
        <div className="flex items-center gap-2 px-4 pt-4 pb-0 border-b border-bg-border">
          {(['open', 'closed'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={clsx(
                'px-4 py-2 text-sm font-medium rounded-t-lg -mb-px border-b-2 transition-colors',
                activeTab === tab
                  ? 'text-accent-blue border-accent-blue'
                  : 'text-gray-500 border-transparent hover:text-gray-300'
              )}
            >
              {tab === 'open' ? `Open Positions (${openTrades.length})` : `Trade History (${closedTrades.length})`}
            </button>
          ))}
        </div>

        <div className="overflow-x-auto">
          {activeTab === 'open' ? (
            <table className="table-dark">
              <thead>
                <tr>
                  <th>Symbol</th>
                  <th>Dir</th>
                  <th>Qty</th>
                  <th>Entry</th>
                  <th>LTP</th>
                  <th>P&L</th>
                  <th>P&L%</th>
                  <th>SL</th>
                  <th>Target</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {openTrades.length === 0 ? (
                  <tr>
                    <td colSpan={10} className="text-center text-gray-600 py-8">
                      No open positions
                    </td>
                  </tr>
                ) : (
                  openTrades.map((t) => {
                    const ltp = t.ltp ?? t.entryPrice;
                    const unrealPnl = t.direction === 'LONG'
                      ? (ltp - t.entryPrice) * t.quantity
                      : (t.entryPrice - ltp) * t.quantity;
                    const unrealPct = ((unrealPnl) / (t.entryPrice * t.quantity)) * 100;
                    return (
                      <tr key={t.id}>
                        <td className="font-bold text-white">{t.symbol}</td>
                        <td>
                          <span className={t.direction === 'LONG' ? 'badge-buy' : 'badge-sell'}>
                            {t.direction}
                          </span>
                        </td>
                        <td className="font-mono">{t.quantity}</td>
                        <td className="font-mono">{formatCurrency(t.entryPrice)}</td>
                        <td className="font-mono">{formatCurrency(ltp)}</td>
                        <td className={clsx('font-mono font-semibold', getChangeColor(unrealPnl))}>
                          {formatCurrency(unrealPnl, 'INR', true)}
                        </td>
                        <td className={clsx('font-mono', getChangeColor(unrealPct))}>
                          {formatPercent(unrealPct)}
                        </td>
                        <td className="font-mono text-red-400">
                          {t.stoploss ? formatCurrency(t.stoploss) : '-'}
                        </td>
                        <td className="font-mono text-emerald-400">
                          {t.target ? formatCurrency(t.target) : '-'}
                        </td>
                        <td>
                          <button
                            onClick={() => setClosingTrade(t)}
                            className="text-xs bg-red-500/20 hover:bg-red-500/40 text-red-400 px-2 py-1 rounded border border-red-500/30 transition-colors"
                          >
                            Close
                          </button>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          ) : (
            <table className="table-dark">
              <thead>
                <tr>
                  <th>Symbol</th>
                  <th>Dir</th>
                  <th>Qty</th>
                  <th>Entry</th>
                  <th>Exit</th>
                  <th>P&L</th>
                  <th>P&L%</th>
                  <th>Exit Reason</th>
                  <th>Closed At</th>
                </tr>
              </thead>
              <tbody>
                {closedTrades.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="text-center text-gray-600 py-8">
                      No closed trades yet
                    </td>
                  </tr>
                ) : (
                  closedTrades.map((t) => (
                    <tr key={t.id}>
                      <td className="font-bold text-white">{t.symbol}</td>
                      <td>
                        <span className={t.direction === 'LONG' ? 'badge-buy' : 'badge-sell'}>
                          {t.direction}
                        </span>
                      </td>
                      <td className="font-mono">{t.quantity}</td>
                      <td className="font-mono">{formatCurrency(t.entryPrice)}</td>
                      <td className="font-mono">{t.exitPrice ? formatCurrency(t.exitPrice) : '-'}</td>
                      <td className={clsx('font-mono font-semibold', getChangeColor(t.pnl ?? 0))}>
                        {t.pnl != null ? formatCurrency(t.pnl, 'INR', true) : '-'}
                      </td>
                      <td className={clsx('font-mono', getChangeColor(t.pnlPercent ?? 0))}>
                        {t.pnlPercent != null ? formatPercent(t.pnlPercent) : '-'}
                      </td>
                      <td className="text-xs text-gray-400">{t.exitReason ?? '-'}</td>
                      <td className="text-xs text-gray-500">
                        {t.exitTime ? formatDateTime(t.exitTime) : '-'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Close Trade Modal */}
      {closingTrade && (
        <CloseModal
          trade={closingTrade}
          onClose={() => setClosingTrade(null)}
          onConfirm={handleCloseTrade}
        />
      )}
    </div>
  );
};

export default Portfolio;
