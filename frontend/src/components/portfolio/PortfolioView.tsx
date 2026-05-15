import React, { useEffect, useCallback } from 'react';
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
import { TrendingUp, TrendingDown, Briefcase, RefreshCw } from 'lucide-react';
import { clsx } from 'clsx';
import { useTradingStore } from '@/store/tradingStore';
import { portfolioApi } from '@/api/portfolio';
import {
  formatCurrency,
  formatPercent,
  getChangeColor,
} from '@/utils/formatters';
import type { PortfolioPosition } from '@/types';

const SECTOR_COLORS = [
  '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6',
  '#ec4899', '#06b6d4', '#84cc16', '#f97316', '#6366f1',
];

// ─── Portfolio Summary Cards ──────────────────────────────────────────────────

function SummaryCards() {
  const summary = useTradingStore((s) => s.portfolioSummary);

  if (!summary) return null;

  const cards = [
    {
      label: 'Invested',
      value: formatCurrency(summary.totalInvested, 'INR', true),
      sub: `${summary.totalPositions} positions`,
      color: 'text-white',
      bg: 'bg-bg-card',
    },
    {
      label: 'Current Value',
      value: formatCurrency(summary.totalCurrentValue, 'INR', true),
      sub: `Unrealized: ${formatPercent(summary.totalUnrealizedPnLPercent)}`,
      color: summary.totalUnrealizedPnL >= 0 ? 'text-emerald-400' : 'text-red-400',
      bg: summary.totalUnrealizedPnL >= 0 ? 'bg-emerald-500/5' : 'bg-red-500/5',
    },
    {
      label: 'Day P&L',
      value: formatCurrency(summary.dayPnL, 'INR', true),
      sub: formatPercent(summary.dayPnLPercent),
      color: summary.dayPnL >= 0 ? 'text-emerald-400' : 'text-red-400',
      bg: summary.dayPnL >= 0 ? 'bg-emerald-500/5' : 'bg-red-500/5',
    },
    {
      label: 'Realized P&L',
      value: formatCurrency(summary.totalRealizedPnL, 'INR', true),
      sub: `${summary.winningPositions}W / ${summary.losingPositions}L`,
      color: summary.totalRealizedPnL >= 0 ? 'text-emerald-400' : 'text-red-400',
      bg: 'bg-bg-card',
    },
    {
      label: 'Available Margin',
      value: formatCurrency(summary.availableMargin, 'INR', true),
      sub: `Used: ${formatCurrency(summary.usedMargin, 'INR', true)}`,
      color: 'text-blue-400',
      bg: 'bg-blue-500/5',
    },
  ];

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-5 gap-3">
      {cards.map((c) => (
        <div
          key={c.label}
          className={clsx('rounded-xl border border-bg-border p-3', c.bg)}
        >
          <div className="text-xs text-gray-500 mb-1">{c.label}</div>
          <div className={clsx('text-lg font-bold font-mono', c.color)}>{c.value}</div>
          <div className="text-xs text-gray-400 mt-0.5">{c.sub}</div>
        </div>
      ))}
    </div>
  );
}

// ─── Holdings Table ───────────────────────────────────────────────────────────

function HoldingsTable({ positions }: { positions: PortfolioPosition[] }) {
  if (positions.length === 0) {
    return (
      <div className="text-center text-gray-500 py-8">No positions found</div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-bg-border text-gray-400 text-xs">
            <th className="text-left py-2 px-3">Symbol</th>
            <th className="text-right py-2 px-3">Qty</th>
            <th className="text-right py-2 px-3">Avg Cost</th>
            <th className="text-right py-2 px-3">LTP</th>
            <th className="text-right py-2 px-3">Invested</th>
            <th className="text-right py-2 px-3">Current</th>
            <th className="text-right py-2 px-3">Unrealized P&L</th>
            <th className="text-right py-2 px-3">Day P&L</th>
            <th className="text-right py-2 px-3">Sector</th>
          </tr>
        </thead>
        <tbody>
          {positions.map((pos) => {
            const pnlColor = pos.unrealizedPnL >= 0 ? 'text-emerald-400' : 'text-red-400';
            const dayColor = pos.dayPnL >= 0 ? 'text-emerald-400' : 'text-red-400';
            return (
              <tr
                key={pos.id}
                className="border-b border-bg-border/50 hover:bg-bg-hover transition-colors"
              >
                <td className="py-2 px-3">
                  <div className="font-bold text-white font-mono">{pos.symbol}</div>
                  <div className="text-xs text-gray-500">{pos.exchange} · {pos.segment}</div>
                </td>
                <td className="text-right py-2 px-3 font-mono text-gray-200">{pos.quantity}</td>
                <td className="text-right py-2 px-3 font-mono text-gray-300">
                  {formatCurrency(pos.averageCost, 'INR')}
                </td>
                <td className="text-right py-2 px-3 font-mono font-semibold text-white">
                  {formatCurrency(pos.lastTradedPrice, 'INR')}
                </td>
                <td className="text-right py-2 px-3 font-mono text-gray-300">
                  {formatCurrency(pos.investedValue, 'INR', true)}
                </td>
                <td className="text-right py-2 px-3 font-mono text-white">
                  {formatCurrency(pos.currentValue, 'INR', true)}
                </td>
                <td className={clsx('text-right py-2 px-3 font-mono font-semibold', pnlColor)}>
                  <div>{formatCurrency(pos.unrealizedPnL, 'INR', true)}</div>
                  <div className="text-xs">{formatPercent(pos.unrealizedPnLPercent)}</div>
                </td>
                <td className={clsx('text-right py-2 px-3 font-mono', dayColor)}>
                  <div>{formatCurrency(pos.dayPnL, 'INR', true)}</div>
                  <div className="text-xs">{formatPercent(pos.dayPnLPercent)}</div>
                </td>
                <td className="text-right py-2 px-3 text-xs text-gray-400">{pos.sector}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

// ─── Sector Pie ───────────────────────────────────────────────────────────────

function SectorPieChart({ positions }: { positions: PortfolioPosition[] }) {
  const sectorMap: Record<string, number> = {};
  positions.forEach((p) => {
    const sector = p.sector || 'Unknown';
    sectorMap[sector] = (sectorMap[sector] || 0) + p.currentValue;
  });

  const data = Object.entries(sectorMap).map(([sector, value]) => ({ sector, value }));

  if (data.length === 0) return null;

  return (
    <div className="rounded-xl bg-bg-card border border-bg-border p-4">
      <div className="text-sm font-semibold text-white mb-3">Sector Allocation</div>
      <ResponsiveContainer width="100%" height={200}>
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="sector"
            cx="50%"
            cy="50%"
            innerRadius={55}
            outerRadius={85}
            paddingAngle={3}
          >
            {data.map((_, i) => (
              <Cell key={i} fill={SECTOR_COLORS[i % SECTOR_COLORS.length]} />
            ))}
          </Pie>
          <Tooltip
            contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', borderRadius: 8, fontSize: 11 }}
            formatter={(val: number) => [formatCurrency(val, 'INR', true), 'Value']}
          />
        </PieChart>
      </ResponsiveContainer>
      <div className="mt-2 grid grid-cols-2 gap-1">
        {data.map((d, i) => (
          <div key={d.sector} className="flex items-center gap-1.5 text-xs">
            <div
              className="w-2 h-2 rounded-full shrink-0"
              style={{ background: SECTOR_COLORS[i % SECTOR_COLORS.length] }}
            />
            <span className="text-gray-400 truncate">{d.sector}</span>
            <span className="ml-auto text-gray-300 font-mono">
              {((d.value / positions.reduce((a, p) => a + p.currentValue, 0)) * 100).toFixed(1)}%
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Performance Chart ────────────────────────────────────────────────────────

function PerformanceChart() {
  // Mock performance data
  const data = Array.from({ length: 90 }, (_, i) => {
    const base = 100;
    const portReturn = base + Math.sin(i / 10) * 8 + i * 0.18 + Math.random() * 2 - 1;
    const niftyReturn = base + Math.sin(i / 12) * 5 + i * 0.12 + Math.random() * 1.5 - 0.75;
    return {
      date: new Date(Date.now() - (90 - i) * 86400000).toLocaleDateString('en-IN', { month: 'short', day: '2-digit' }),
      portfolio: Number(portReturn.toFixed(2)),
      nifty: Number(niftyReturn.toFixed(2)),
    };
  });

  return (
    <div className="rounded-xl bg-bg-card border border-bg-border p-4">
      <div className="text-sm font-semibold text-white mb-3">Performance vs Nifty 50 (90d)</div>
      <ResponsiveContainer width="100%" height={200}>
        <LineChart data={data} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#1c2333" vertical={false} />
          <XAxis dataKey="date" tick={{ fill: '#6b7280', fontSize: 10 }} axisLine={false} tickLine={false} interval={14} />
          <YAxis
            tick={{ fill: '#6b7280', fontSize: 10 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => `${v.toFixed(0)}`}
            domain={['auto', 'auto']}
          />
          <Tooltip
            contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', borderRadius: 8, fontSize: 11 }}
            formatter={(val: number, name: string) => [
              `${val.toFixed(2)} (${(val - 100).toFixed(2)}%)`,
              name === 'portfolio' ? 'Portfolio' : 'Nifty 50',
            ]}
          />
          <Legend
            formatter={(v) => <span style={{ color: '#9ca3af', fontSize: 11 }}>{v === 'portfolio' ? 'Portfolio' : 'Nifty 50'}</span>}
          />
          <Line dataKey="portfolio" stroke="#3b82f6" dot={false} strokeWidth={2} name="portfolio" />
          <Line dataKey="nifty" stroke="#6b7280" dot={false} strokeWidth={1.5} strokeDasharray="5 3" name="nifty" />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export const PortfolioView: React.FC = () => {
  const {
    portfolioPositions,
    portfolioLoading,
    setPortfolioPositions,
    setPortfolioSummary,
    setPortfolioLoading,
  } = useTradingStore();

  const fetchPortfolio = useCallback(async () => {
    setPortfolioLoading(true);
    try {
      const [positions, summary] = await Promise.all([
        portfolioApi.getPositions(),
        portfolioApi.getSummary(),
      ]);
      setPortfolioPositions(positions);
      setPortfolioSummary(summary);
    } catch (err) {
      console.error('[PortfolioView] fetch error:', err);
    } finally {
      setPortfolioLoading(false);
    }
  }, [setPortfolioPositions, setPortfolioSummary, setPortfolioLoading]);

  useEffect(() => {
    fetchPortfolio();
  }, [fetchPortfolio]);

  return (
    <div className="space-y-4">
      {/* Summary */}
      <SummaryCards />

      {/* Holdings Table */}
      <div className="rounded-xl bg-bg-card border border-bg-border overflow-hidden">
        <div className="px-4 py-3 border-b border-bg-border flex items-center gap-2">
          <Briefcase size={16} className="text-accent-blue" />
          <h2 className="font-semibold text-white text-sm">Holdings</h2>
          <span className="text-xs text-gray-500 ml-1">({portfolioPositions.length} positions)</span>
          <button
            onClick={fetchPortfolio}
            className="ml-auto text-gray-400 hover:text-white transition-colors"
            disabled={portfolioLoading}
          >
            <RefreshCw size={14} className={portfolioLoading ? 'animate-spin' : ''} />
          </button>
        </div>
        {portfolioLoading ? (
          <div className="flex items-center justify-center py-12 text-gray-500">
            <RefreshCw size={16} className="animate-spin mr-2" />
            Loading portfolio...
          </div>
        ) : (
          <HoldingsTable positions={portfolioPositions} />
        )}
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2">
          <PerformanceChart />
        </div>
        <SectorPieChart positions={portfolioPositions} />
      </div>
    </div>
  );
};

export default PortfolioView;
