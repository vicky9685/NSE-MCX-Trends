import React, { useState, useEffect, useMemo } from 'react';
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts';
import { Shield, AlertTriangle, TrendingDown, Gauge, Globe, Moon } from 'lucide-react';
import { clsx } from 'clsx';
import { portfolioApi } from '@/api/portfolio';
import { useTradingStore } from '@/store/tradingStore';
import type { RiskMetrics, DrawdownDataPoint } from '@/types';
import { formatCurrency, formatPercent } from '@/utils/formatters';

// ─── Position Size Calculator ─────────────────────────────────────────────────

interface CalcResult {
  quantity: number;
  positionValue: number;
  amountAtRisk: number;
}

function calcPositionSize(
  capital: number,
  riskPct: number,
  entryPrice: number,
  stopLoss: number
): CalcResult {
  if (!entryPrice || !stopLoss || entryPrice <= stopLoss) {
    return { quantity: 0, positionValue: 0, amountAtRisk: 0 };
  }
  const amountAtRisk = capital * (riskPct / 100);
  const riskPerShare = entryPrice - stopLoss;
  const quantity = Math.floor(amountAtRisk / riskPerShare);
  const positionValue = quantity * entryPrice;
  return { quantity, positionValue, amountAtRisk };
}

// ─── VIX Gauge ────────────────────────────────────────────────────────────────

const VixGauge: React.FC<{ vix: number }> = ({ vix }) => {
  const pct = Math.min((vix / 40) * 100, 100);
  const color = vix < 15 ? '#10b981' : vix < 20 ? '#22c55e' : vix < 25 ? '#eab308' : vix < 30 ? '#f97316' : '#ef4444';
  const label = vix < 15 ? 'Low' : vix < 20 ? 'Normal' : vix < 25 ? 'Elevated' : vix < 30 ? 'High' : 'Extreme';

  return (
    <div className="flex flex-col items-center gap-3">
      <div className="relative w-36 h-20 overflow-hidden">
        {/* Track */}
        <svg viewBox="0 0 120 70" className="w-full">
          <path d="M10,60 A50,50 0 0,1 110,60" fill="none" stroke="#2d3748" strokeWidth="12" strokeLinecap="round" />
          <path
            d="M10,60 A50,50 0 0,1 110,60"
            fill="none"
            stroke={color}
            strokeWidth="12"
            strokeLinecap="round"
            strokeDasharray={`${(pct / 100) * 157} 157`}
          />
          <text x="60" y="55" textAnchor="middle" fill="white" fontSize="16" fontWeight="bold" fontFamily="monospace">
            {vix.toFixed(1)}
          </text>
        </svg>
      </div>
      <div className="flex items-center gap-2">
        <span className="text-xs text-gray-400">India VIX:</span>
        <span className="font-bold text-sm" style={{ color }}>{label}</span>
      </div>
    </div>
  );
};

// ─── Risk Indicator Card ───────────────────────────────────────────────────────

interface RiskIndicatorProps {
  label: string;
  value: number;
  icon: React.ReactNode;
  description: string;
}

const RiskIndicator: React.FC<RiskIndicatorProps> = ({ label, value, icon, description }) => {
  const pct = Math.min(value * 100, 100);
  const color =
    pct < 20 ? 'bg-emerald-500' : pct < 40 ? 'bg-yellow-500' : pct < 60 ? 'bg-orange-500' : 'bg-red-500';
  const textColor =
    pct < 20 ? 'text-emerald-400' : pct < 40 ? 'text-yellow-400' : pct < 60 ? 'text-orange-400' : 'text-red-400';

  return (
    <div className="bg-bg-card border border-bg-border rounded-xl p-4">
      <div className="flex items-center gap-2 mb-3">
        <span className="text-gray-500">{icon}</span>
        <span className="text-sm font-semibold text-gray-300">{label}</span>
      </div>
      <div className="w-full bg-bg-tertiary rounded-full h-2 mb-2">
        <div className={clsx('h-2 rounded-full transition-all', color)} style={{ width: `${pct}%` }} />
      </div>
      <div className="flex justify-between items-center">
        <span className="text-xs text-gray-500">{description}</span>
        <span className={clsx('text-xs font-bold', textColor)}>{(pct).toFixed(1)}%</span>
      </div>
    </div>
  );
};

// ─── Main RiskManagement Component ────────────────────────────────────────────

export const RiskManagement: React.FC = () => {
  const [capital, setCapital] = useState(1000000);
  const [riskPct, setRiskPct] = useState(1);
  const [entryPrice, setEntryPrice] = useState(0);
  const [stopLoss, setStopLoss] = useState(0);
  const [riskMetrics, setRiskMetrics] = useState<RiskMetrics | null>(null);
  const [drawdownData, setDrawdownData] = useState<DrawdownDataPoint[]>([]);
  const [loading, setLoading] = useState(false);

  const indiaVix = useTradingStore((s) => s.indiaVix);
  const indiaVixChange = useTradingStore((s) => s.indiaVixChange);

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      try {
        const metrics = await portfolioApi.getRiskMetrics();
        setRiskMetrics(metrics);
        // Generate drawdown curve from performance data
        const perf = await portfolioApi.getPerformance(90);
        let peak = perf[0]?.portfolioValue ?? 0;
        const dd: DrawdownDataPoint[] = perf.map((p) => {
          if (p.portfolioValue > peak) peak = p.portfolioValue;
          const drawdown = peak > 0 ? ((p.portfolioValue - peak) / peak) * 100 : 0;
          return { date: p.date, drawdown };
        });
        setDrawdownData(dd);
      } catch {
        // Use empty state gracefully
      } finally {
        setLoading(false);
      }
    };
    fetch();
  }, []);

  const calc = useMemo(
    () => calcPositionSize(capital, riskPct, entryPrice, stopLoss),
    [capital, riskPct, entryPrice, stopLoss]
  );

  // Sector risk data (mock if no metrics)
  const sectorRiskData = [
    { name: 'Banking', risk: 28 },
    { name: 'IT', risk: 18 },
    { name: 'FMCG', risk: 8 },
    { name: 'Pharma', risk: 12 },
    { name: 'Auto', risk: 15 },
    { name: 'Metals', risk: 10 },
    { name: 'Infra', risk: 9 },
  ];

  const volatilityWarnings = [
    indiaVix > 20 && { id: 1, msg: `India VIX at ${indiaVix.toFixed(1)} — elevated volatility, reduce position sizes` },
    riskMetrics && riskMetrics.maxDrawdown < -0.15 && { id: 2, msg: `Max drawdown ${formatPercent(riskMetrics.maxDrawdown * 100)} — consider de-risking` },
    riskMetrics && riskMetrics.concentrationRisk > 0.3 && { id: 3, msg: 'High concentration risk detected in portfolio — diversify across sectors' },
    riskMetrics && riskMetrics.beta > 1.3 && { id: 4, msg: `Portfolio beta ${riskMetrics.beta.toFixed(2)} — high market sensitivity` },
  ].filter(Boolean) as Array<{ id: number; msg: string }>;

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-lg font-bold text-white">Risk Management</h1>
        <p className="text-xs text-gray-500">Position sizing, VaR, drawdown analysis & risk controls</p>
      </div>

      {/* Top Row: Position Sizer + VaR + VIX */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Position Size Calculator */}
        <div className="bg-bg-card border border-bg-border rounded-xl p-5">
          <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
            <Shield size={15} className="text-accent-blue" />
            Position Size Calculator
          </h3>
          <div className="space-y-3">
            <div>
              <label className="label-form">Capital (INR)</label>
              <input
                type="number"
                value={capital}
                onChange={(e) => setCapital(Number(e.target.value))}
                className="input-dark w-full"
              />
            </div>
            <div>
              <label className="label-form">Risk % — {riskPct.toFixed(1)}%</label>
              <input
                type="range"
                min={0.5}
                max={5}
                step={0.5}
                value={riskPct}
                onChange={(e) => setRiskPct(Number(e.target.value))}
                className="w-full accent-accent-blue"
              />
              <div className="flex justify-between text-xs text-gray-600 mt-0.5">
                <span>0.5%</span><span>Conservative</span><span>5%</span>
              </div>
            </div>
            <div>
              <label className="label-form">Entry Price</label>
              <input
                type="number"
                value={entryPrice || ''}
                onChange={(e) => setEntryPrice(Number(e.target.value))}
                placeholder="e.g. 2500"
                className="input-dark w-full"
                step="0.05"
              />
            </div>
            <div>
              <label className="label-form">Stop Loss</label>
              <input
                type="number"
                value={stopLoss || ''}
                onChange={(e) => setStopLoss(Number(e.target.value))}
                placeholder="e.g. 2450"
                className="input-dark w-full"
                step="0.05"
              />
            </div>
          </div>
          {/* Results */}
          <div className="mt-4 space-y-2 bg-bg-tertiary rounded-lg p-3 border border-bg-border">
            <div className="flex justify-between text-sm">
              <span className="text-gray-400">Quantity</span>
              <span className="font-bold text-white font-mono">{calc.quantity.toLocaleString('en-IN')}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-gray-400">Position Value</span>
              <span className="font-mono text-accent-blue-light">{formatCurrency(calc.positionValue, 'INR', true)}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-gray-400">Amount at Risk</span>
              <span className="font-mono text-red-400">{formatCurrency(calc.amountAtRisk, 'INR', true)}</span>
            </div>
          </div>
        </div>

        {/* VaR Card */}
        <div className="bg-bg-card border border-bg-border rounded-xl p-5">
          <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
            <TrendingDown size={15} className="text-red-400" />
            Value at Risk (VaR)
          </h3>
          {riskMetrics ? (
            <div className="space-y-4">
              <div className="bg-red-500/10 border border-red-500/20 rounded-lg p-3">
                <div className="text-xs text-red-400 font-semibold mb-1">VaR 95% (1-Day)</div>
                <div className="text-2xl font-bold text-red-400 font-mono">
                  {formatCurrency(Math.abs(riskMetrics.portfolioVaR95 * capital), 'INR', true)}
                </div>
                <div className="text-xs text-gray-500 mt-1">
                  {formatPercent(riskMetrics.portfolioVaR95 * 100)} of portfolio
                </div>
              </div>
              <div className="bg-orange-500/10 border border-orange-500/20 rounded-lg p-3">
                <div className="text-xs text-orange-400 font-semibold mb-1">VaR 99% (1-Day)</div>
                <div className="text-xl font-bold text-orange-400 font-mono">
                  {formatCurrency(Math.abs(riskMetrics.portfolioVaR99 * capital), 'INR', true)}
                </div>
              </div>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-400">Expected Shortfall</span>
                  <span className="text-red-400 font-mono">{formatPercent(riskMetrics.expectedShortfall * 100)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-400">Sharpe Ratio</span>
                  <span className={riskMetrics.sharpeRatio >= 1 ? 'text-emerald-400' : 'text-yellow-400'}>
                    {riskMetrics.sharpeRatio.toFixed(2)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-400">Beta to Nifty</span>
                  <span className={Math.abs(riskMetrics.beta - 1) < 0.2 ? 'text-emerald-400' : 'text-orange-400'}>
                    {riskMetrics.beta.toFixed(2)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-400">Volatility (ann.)</span>
                  <span className="text-white font-mono">{formatPercent(riskMetrics.volatility * 100)}</span>
                </div>
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-center h-40 text-gray-600 text-sm">
              {loading ? 'Loading...' : 'No risk data available'}
            </div>
          )}
        </div>

        {/* VIX Gauge */}
        <div className="bg-bg-card border border-bg-border rounded-xl p-5">
          <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
            <Gauge size={15} className="text-yellow-400" />
            India VIX Meter
          </h3>
          <VixGauge vix={indiaVix || 17.5} />
          <div className="mt-3 flex justify-center">
            <span className={clsx('text-xs font-semibold', indiaVixChange >= 0 ? 'text-red-400' : 'text-emerald-400')}>
              {indiaVixChange >= 0 ? '+' : ''}{indiaVixChange.toFixed(2)} today
            </span>
          </div>
          <div className="mt-4 space-y-2">
            <RiskIndicator
              label="Overnight Gap Risk"
              value={riskMetrics?.overnightGapRisk ?? 0.18}
              icon={<Moon size={13} />}
              description="Based on SGX Nifty & US futures"
            />
            <RiskIndicator
              label="Global Shock Risk"
              value={riskMetrics?.globalShockRisk ?? 0.12}
              icon={<Globe size={13} />}
              description="Fed, geopolitical, macro events"
            />
          </div>
        </div>
      </div>

      {/* Max Drawdown Chart */}
      <div className="bg-bg-card border border-bg-border rounded-xl p-5">
        <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
          <TrendingDown size={15} className="text-red-400" />
          Portfolio Drawdown
          {riskMetrics && (
            <span className="ml-auto text-xs text-gray-400">
              Max: <span className="text-red-400 font-bold">{formatPercent(riskMetrics.maxDrawdown * 100)}</span>
              {' '}&nbsp; Current: <span className={clsx('font-bold', getDrawdownColor(riskMetrics.currentDrawdown))}>
                {formatPercent(riskMetrics.currentDrawdown * 100)}
              </span>
            </span>
          )}
        </h3>
        {drawdownData.length > 0 ? (
          <ResponsiveContainer width="100%" height={180}>
            <AreaChart data={drawdownData} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2d3748" />
              <XAxis dataKey="date" tick={{ fill: '#6b7280', fontSize: 10 }} tickFormatter={(v) => v.slice(5)} />
              <YAxis tick={{ fill: '#6b7280', fontSize: 10 }} tickFormatter={(v) => `${v.toFixed(1)}%`} />
              <Tooltip
                contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', fontSize: 12 }}
                formatter={(val: number) => [`${val.toFixed(2)}%`, 'Drawdown']}
              />
              <Area
                type="monotone"
                dataKey="drawdown"
                stroke="#ef4444"
                fill="#ef4444"
                fillOpacity={0.15}
                strokeWidth={1.5}
              />
            </AreaChart>
          </ResponsiveContainer>
        ) : (
          <div className="flex items-center justify-center h-[180px] text-gray-600 text-sm">
            No drawdown data available
          </div>
        )}
      </div>

      {/* Bottom Row: Sector Risk + Warnings */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* Sector Risk Allocation */}
        <div className="bg-bg-card border border-bg-border rounded-xl p-5">
          <h3 className="text-sm font-bold text-white mb-4">Risk Allocation by Sector</h3>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={sectorRiskData} layout="vertical" margin={{ top: 0, right: 20, left: 10, bottom: 0 }}>
              <XAxis type="number" tick={{ fill: '#6b7280', fontSize: 10 }} unit="%" />
              <YAxis type="category" dataKey="name" tick={{ fill: '#9ca3af', fontSize: 11 }} width={55} />
              <Tooltip
                contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', fontSize: 12 }}
                formatter={(val: number) => [`${val}%`, 'Risk Weight']}
              />
              <Bar dataKey="risk" radius={[0, 3, 3, 0]}>
                {sectorRiskData.map((entry, i) => (
                  <Cell
                    key={i}
                    fill={entry.risk > 25 ? '#ef4444' : entry.risk > 15 ? '#f97316' : '#3b82f6'}
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Volatility Warnings */}
        <div className="bg-bg-card border border-bg-border rounded-xl p-5">
          <h3 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
            <AlertTriangle size={15} className="text-yellow-400" />
            Risk Warnings
          </h3>
          {volatilityWarnings.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-32 text-gray-600 text-sm gap-2">
              <Shield size={24} className="text-emerald-500" />
              <span>No active risk warnings</span>
            </div>
          ) : (
            <div className="space-y-3">
              {volatilityWarnings.map((w) => (
                <div
                  key={w.id}
                  className="flex gap-3 bg-yellow-500/10 border border-yellow-500/20 rounded-lg p-3"
                >
                  <AlertTriangle size={14} className="text-yellow-400 shrink-0 mt-0.5" />
                  <p className="text-xs text-yellow-200 leading-relaxed">{w.msg}</p>
                </div>
              ))}
            </div>
          )}
          <div className="mt-4 pt-4 border-t border-bg-border space-y-2">
            <div className="text-xs text-gray-500 font-semibold uppercase tracking-wider">Quick Metrics</div>
            {[
              { label: 'Concentration Risk', val: riskMetrics ? `${(riskMetrics.concentrationRisk * 100).toFixed(1)}%` : '—' },
              { label: 'Correlation to Nifty', val: riskMetrics ? riskMetrics.correlationToNifty.toFixed(2) : '—' },
              { label: 'Alpha (Ann.)', val: riskMetrics ? `${(riskMetrics.alpha * 100).toFixed(2)}%` : '—' },
            ].map((m) => (
              <div key={m.label} className="flex justify-between text-sm">
                <span className="text-gray-400">{m.label}</span>
                <span className="text-white font-mono">{m.val}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

function getDrawdownColor(dd: number): string {
  if (dd >= -0.05) return 'text-emerald-400';
  if (dd >= -0.1) return 'text-yellow-400';
  if (dd >= -0.2) return 'text-orange-400';
  return 'text-red-400';
}

export default RiskManagement;
