import React, { useRef, useEffect } from 'react';
import { AlertTriangle, TrendingUp, Shield, Activity, BarChart2, RefreshCw } from 'lucide-react';
import { clsx } from 'clsx';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell,
} from 'recharts';
import { SentimentGauge } from './SentimentGauge';
import { SignalCard } from './SignalCard';
import { GlobalMacroPanel } from './GlobalMacroPanel';
import { useTradingStore } from '@/store/tradingStore';
import { useMarketData } from '@/hooks/useMarketData';
import { formatCurrency, formatPercent, getChangeColor, formatNumber } from '@/utils/formatters';
import type { MarketDashboard as MarketDashboardType, TickerItem } from '@/types';

// ─── Ticker Strip ─────────────────────────────────────────────────────────────

function TickerStrip({ items }: { items: TickerItem[] }) {
  const ref = useRef<HTMLDivElement>(null);

  if (!items || items.length === 0) return null;

  return (
    <div className="overflow-hidden bg-bg-secondary border-b border-bg-border py-1.5 relative">
      <div className="flex animate-ticker whitespace-nowrap" ref={ref}>
        {[...items, ...items].map((item, idx) => (
          <span key={idx} className="inline-flex items-center gap-2 mr-8">
            <span className="text-xs font-mono font-semibold text-gray-200">{item.symbol}</span>
            <span className="text-xs font-mono text-white">{item.ltp.toFixed(2)}</span>
            <span
              className={clsx(
                'text-xs font-mono font-bold',
                item.changePercent >= 0 ? 'text-emerald-400' : 'text-red-400'
              )}
            >
              {item.changePercent >= 0 ? '+' : ''}
              {item.changePercent.toFixed(2)}%
            </span>
          </span>
        ))}
      </div>
    </div>
  );
}

// ─── Index Card ───────────────────────────────────────────────────────────────

function IndexCard({ label, price, change, changePercent }: {
  label: string;
  price: number;
  change: number;
  changePercent: number;
}) {
  const positive = changePercent >= 0;
  return (
    <div className="rounded-xl bg-bg-card border border-bg-border px-4 py-3 flex flex-col gap-0.5">
      <div className="text-xs text-gray-500 font-semibold uppercase tracking-wide">{label}</div>
      <div className="text-lg font-bold font-mono text-white">
        {price.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
      </div>
      <div className={clsx('text-xs font-mono font-semibold', positive ? 'text-emerald-400' : 'text-red-400')}>
        {positive ? '+' : ''}{change.toFixed(2)} ({positive ? '+' : ''}{changePercent.toFixed(2)}%)
      </div>
    </div>
  );
}

// ─── FII DII Chart ────────────────────────────────────────────────────────────

function FIIDIIChart({ data }: { data: MarketDashboardType['moneyFlow'] }) {
  if (!data || data.length === 0) return <div className="text-gray-500 text-xs p-4">No FII/DII data</div>;

  const chartData = data.slice(-15).map((d) => ({
    date: d.date.slice(5),
    fii: d.fiiNet / 1e7,
    dii: d.diiNet / 1e7,
  }));

  return (
    <ResponsiveContainer width="100%" height={160}>
      <BarChart data={chartData} barGap={2} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
        <XAxis dataKey="date" tick={{ fill: '#6b7280', fontSize: 10 }} axisLine={false} tickLine={false} />
        <YAxis tick={{ fill: '#6b7280', fontSize: 10 }} axisLine={false} tickLine={false} width={40} tickFormatter={(v) => `${v}Cr`} />
        <Tooltip
          contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', borderRadius: 8 }}
          labelStyle={{ color: '#9ca3af' }}
          itemStyle={{ color: '#e2e8f0' }}
          formatter={(val: number) => [`₹${val.toFixed(2)}Cr`, '']}
        />
        <Bar dataKey="fii" name="FII" radius={[2, 2, 0, 0]}>
          {chartData.map((d, i) => (
            <Cell key={i} fill={d.fii >= 0 ? '#10b981' : '#ef4444'} />
          ))}
        </Bar>
        <Bar dataKey="dii" name="DII" radius={[2, 2, 0, 0]}>
          {chartData.map((d, i) => (
            <Cell key={i} fill={d.dii >= 0 ? '#3b82f6' : '#f97316'} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

// ─── Sector Heatmap ───────────────────────────────────────────────────────────

function SectorHeatmap({ sectors }: { sectors: MarketDashboardType['sectorHeatmap'] }) {
  if (!sectors || sectors.length === 0) return <div className="text-gray-500 text-xs">No sector data</div>;

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2">
      {sectors.map((s) => {
        const positive = s.change >= 0;
        const intensity = Math.min(Math.abs(s.change) / 5, 1);
        const bg = positive
          ? `rgba(16,185,129,${0.08 + intensity * 0.3})`
          : `rgba(239,68,68,${0.08 + intensity * 0.3})`;
        const border = positive ? '#10b98133' : '#ef444433';

        return (
          <div
            key={s.sector}
            className="rounded-lg p-2 border text-center"
            style={{ background: bg, borderColor: border }}
          >
            <div className="text-xs font-semibold text-white truncate">{s.sector}</div>
            <div
              className={clsx(
                'text-sm font-bold font-mono mt-0.5',
                positive ? 'text-emerald-400' : 'text-red-400'
              )}
            >
              {positive ? '+' : ''}{s.change.toFixed(2)}%
            </div>
            {s.topGainer && (
              <div className="text-xs text-emerald-400 truncate">▲ {s.topGainer}</div>
            )}
            {s.topLoser && (
              <div className="text-xs text-red-400 truncate">▼ {s.topLoser}</div>
            )}
          </div>
        );
      })}
    </div>
  );
}

// ─── Market Breadth ───────────────────────────────────────────────────────────

function BreadthCard({ dashboard }: { dashboard: MarketDashboardType }) {
  const breadth = dashboard.nifty50; // Using as placeholder; real breadth from API
  const total = 50;
  return (
    <div className="rounded-xl bg-bg-card border border-bg-border p-4">
      <div className="flex items-center gap-2 mb-3">
        <Activity size={14} className="text-accent-blue" />
        <span className="text-sm font-semibold text-white">Market Status</span>
        <span
          className={clsx(
            'ml-auto text-xs px-2 py-0.5 rounded-full font-semibold',
            dashboard.marketStatus === 'OPEN'
              ? 'bg-emerald-500/20 text-emerald-400'
              : 'bg-red-500/20 text-red-400'
          )}
        >
          {dashboard.marketStatus}
        </span>
      </div>
      <div className="text-xs text-gray-400">Session: {dashboard.sessionType}</div>
    </div>
  );
}

// ─── Main Dashboard ───────────────────────────────────────────────────────────

export const MarketDashboard: React.FC = () => {
  const { refetch } = useMarketData();
  const dashboard = useTradingStore((s) => s.dashboard);
  const loading = useTradingStore((s) => s.dashboardLoading);
  const globalMacro = useTradingStore((s) => s.globalMacro);

  if (loading && !dashboard) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="flex items-center gap-3 text-gray-400">
          <RefreshCw size={20} className="animate-spin" />
          <span>Loading market data...</span>
        </div>
      </div>
    );
  }

  if (!dashboard) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">No dashboard data available</div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Live Ticker */}
      {dashboard.tickerData && <TickerStrip items={dashboard.tickerData} />}

      {/* Top Index Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {dashboard.nifty50 && (
          <IndexCard
            label="Nifty 50"
            price={dashboard.nifty50.lastTradedPrice}
            change={dashboard.nifty50.change}
            changePercent={dashboard.nifty50.changePercent}
          />
        )}
        {dashboard.bankNifty && (
          <IndexCard
            label="Bank Nifty"
            price={dashboard.bankNifty.lastTradedPrice}
            change={dashboard.bankNifty.change}
            changePercent={dashboard.bankNifty.changePercent}
          />
        )}
        {dashboard.sensex && (
          <IndexCard
            label="Sensex"
            price={dashboard.sensex.lastTradedPrice}
            change={dashboard.sensex.change}
            changePercent={dashboard.sensex.changePercent}
          />
        )}
        {dashboard.niftyMidcap && (
          <IndexCard
            label="Nifty Midcap"
            price={dashboard.niftyMidcap.lastTradedPrice}
            change={dashboard.niftyMidcap.change}
            changePercent={dashboard.niftyMidcap.changePercent}
          />
        )}
      </div>

      {/* Main Grid: Sentiment + VIX + FII/DII */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Sentiment Gauge */}
        <div className="rounded-xl bg-bg-card border border-bg-border p-4 flex flex-col items-center">
          <div className="text-sm font-semibold text-white mb-2">Market Sentiment</div>
          <SentimentGauge score={dashboard.sentimentScore} size={220} />
        </div>

        {/* India VIX Card */}
        <div className="rounded-xl bg-bg-card border border-bg-border p-4 flex flex-col gap-3">
          <div className="flex items-center gap-2">
            <Activity size={16} className="text-accent-blue" />
            <span className="font-semibold text-white text-sm">India VIX Outlook</span>
          </div>
          <div className="flex items-end gap-3">
            <div>
              <div
                className={clsx(
                  'text-4xl font-bold font-mono',
                  dashboard.indiaVix > 20
                    ? 'text-red-400'
                    : dashboard.indiaVix > 15
                    ? 'text-yellow-400'
                    : 'text-emerald-400'
                )}
              >
                {dashboard.indiaVix.toFixed(2)}
              </div>
              <div
                className={clsx(
                  'text-sm font-mono font-semibold mt-1',
                  dashboard.indiaVixChange >= 0 ? 'text-red-400' : 'text-emerald-400'
                )}
              >
                {dashboard.indiaVixChange >= 0 ? '+' : ''}{dashboard.indiaVixChange.toFixed(2)}%
              </div>
            </div>
            <div className="text-xs text-gray-400 mb-1 leading-relaxed">
              {dashboard.indiaVix > 25
                ? '⚠️ Extreme Fear — High hedging cost. Consider defensive positions.'
                : dashboard.indiaVix > 20
                ? '⚡ Elevated volatility. Use options carefully.'
                : dashboard.indiaVix > 15
                ? '📊 Moderate volatility. Normal conditions.'
                : '✅ Low volatility. Favorable for trend trades.'}
            </div>
          </div>
          <div className="rounded-lg bg-bg-tertiary p-3 text-xs text-gray-300 leading-relaxed border border-bg-border">
            {dashboard.capitalProtectionStrategy}
          </div>
        </div>

        {/* Market Status + Breadth */}
        <div className="space-y-3">
          <BreadthCard dashboard={dashboard} />
          {/* Top Risks */}
          <div className="rounded-xl bg-bg-card border border-bg-border p-4">
            <div className="flex items-center gap-2 mb-2">
              <AlertTriangle size={14} className="text-yellow-400" />
              <span className="text-sm font-semibold text-white">Key Risks Today</span>
            </div>
            <ul className="space-y-1">
              {(dashboard.topRisks || []).slice(0, 4).map((risk, i) => (
                <li key={i} className="text-xs text-gray-400 flex items-start gap-1.5">
                  <span className="text-yellow-400 mt-0.5">•</span>
                  {risk}
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>

      {/* FII/DII + Sector Heatmap */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="rounded-xl bg-bg-card border border-bg-border p-4">
          <div className="flex items-center gap-2 mb-3">
            <BarChart2 size={14} className="text-accent-blue" />
            <span className="text-sm font-semibold text-white">FII / DII Money Flow (₹Cr)</span>
            <div className="ml-auto flex items-center gap-3 text-xs">
              <span className="flex items-center gap-1">
                <span className="w-2 h-2 rounded-sm bg-emerald-500 inline-block" /> FII
              </span>
              <span className="flex items-center gap-1">
                <span className="w-2 h-2 rounded-sm bg-blue-500 inline-block" /> DII
              </span>
            </div>
          </div>
          <FIIDIIChart data={dashboard.moneyFlow} />
        </div>

        <div className="rounded-xl bg-bg-card border border-bg-border p-4">
          <div className="flex items-center gap-2 mb-3">
            <TrendingUp size={14} className="text-accent-blue" />
            <span className="text-sm font-semibold text-white">Sector Heatmap</span>
          </div>
          <SectorHeatmap sectors={dashboard.sectorHeatmap} />
        </div>
      </div>

      {/* Top Opportunities */}
      {dashboard.topSignals && dashboard.topSignals.length > 0 && (
        <div>
          <div className="flex items-center gap-2 mb-3">
            <TrendingUp size={14} className="text-accent-blue" />
            <h2 className="text-sm font-semibold text-white">Top Opportunities Today</h2>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
            {dashboard.topSignals.slice(0, 6).map((signal) => (
              <SignalCard key={signal.id} signal={signal} />
            ))}
          </div>
        </div>
      )}

      {/* Global Macro */}
      <GlobalMacroPanel data={globalMacro} />
    </div>
  );
};

export default MarketDashboard;
