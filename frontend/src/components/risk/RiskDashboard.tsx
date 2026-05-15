import React, { useState, useCallback } from 'react';
import {
  ComposedChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  ReferenceLine,
} from 'recharts';
import { AlertTriangle, Shield, Calculator, TrendingDown, Zap } from 'lucide-react';
import { clsx } from 'clsx';
import { useTradingStore } from '@/store/tradingStore';
import { formatCurrency, formatPercent } from '@/utils/formatters';
import type { RiskMetrics, PositionSizeResult } from '@/types';

// ─── Position Size Calculator ─────────────────────────────────────────────────

interface CalcForm {
  capital: string;
  riskPercent: string;
  entryPrice: string;
  stopLoss: string;
  targetPrice: string;
}

function PositionSizeCalculator() {
  const [form, setForm] = useState<CalcForm>({
    capital: '1000000',
    riskPercent: '1',
    entryPrice: '',
    stopLoss: '',
    targetPrice: '',
  });
  const [result, setResult] = useState<PositionSizeResult | null>(null);

  const calculate = useCallback(() => {
    const capital = parseFloat(form.capital);
    const riskPct = parseFloat(form.riskPercent) / 100;
    const entry = parseFloat(form.entryPrice);
    const sl = parseFloat(form.stopLoss);
    const target = parseFloat(form.targetPrice);

    if (!capital || !riskPct || !entry || !sl) return;

    const riskAmount = capital * riskPct;
    const slDistance = Math.abs(entry - sl);
    if (slDistance === 0) return;

    const quantity = Math.floor(riskAmount / slDistance);
    const positionValue = quantity * entry;
    const rewardAmount = target ? quantity * Math.abs(target - entry) : 0;
    const rewardPercent = rewardAmount / capital;

    setResult({
      quantity,
      positionValue,
      riskAmount,
      rewardAmount,
      riskPercent: riskPct,
      rewardPercent,
      stopLossAmount: quantity * slDistance,
    });
  }, [form]);

  const inputClass =
    'w-full bg-bg-tertiary border border-bg-border rounded-lg px-3 py-2 text-sm text-white font-mono focus:border-accent-blue focus:outline-none transition-colors';
  const labelClass = 'text-xs text-gray-400 mb-1 block';

  return (
    <div className="rounded-xl bg-bg-card border border-bg-border p-4">
      <div className="flex items-center gap-2 mb-4">
        <Calculator size={16} className="text-accent-blue" />
        <h3 className="font-semibold text-white text-sm">Position Size Calculator</h3>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>Capital (₹)</label>
          <input
            className={inputClass}
            type="number"
            value={form.capital}
            onChange={(e) => setForm((f) => ({ ...f, capital: e.target.value }))}
            placeholder="1000000"
          />
        </div>
        <div>
          <label className={labelClass}>Risk % per Trade</label>
          <input
            className={inputClass}
            type="number"
            step="0.1"
            value={form.riskPercent}
            onChange={(e) => setForm((f) => ({ ...f, riskPercent: e.target.value }))}
            placeholder="1"
          />
        </div>
        <div>
          <label className={labelClass}>Entry Price</label>
          <input
            className={inputClass}
            type="number"
            step="0.05"
            value={form.entryPrice}
            onChange={(e) => setForm((f) => ({ ...f, entryPrice: e.target.value }))}
            placeholder="0.00"
          />
        </div>
        <div>
          <label className={labelClass}>Stop Loss</label>
          <input
            className={inputClass}
            type="number"
            step="0.05"
            value={form.stopLoss}
            onChange={(e) => setForm((f) => ({ ...f, stopLoss: e.target.value }))}
            placeholder="0.00"
          />
        </div>
        <div>
          <label className={labelClass}>Target Price (optional)</label>
          <input
            className={inputClass}
            type="number"
            step="0.05"
            value={form.targetPrice}
            onChange={(e) => setForm((f) => ({ ...f, targetPrice: e.target.value }))}
            placeholder="0.00"
          />
        </div>
        <div className="flex items-end">
          <button
            onClick={calculate}
            className="w-full bg-accent-blue hover:bg-accent-blue-dark text-white rounded-lg py-2 text-sm font-semibold transition-colors"
          >
            Calculate
          </button>
        </div>
      </div>

      {result && (
        <div className="mt-4 rounded-lg bg-bg-tertiary border border-bg-border p-3 animate-fade-in">
          <div className="grid grid-cols-2 gap-3">
            <div className="text-center p-2 rounded-lg bg-accent-blue/10 border border-accent-blue/20">
              <div className="text-2xl font-bold text-white font-mono">{result.quantity}</div>
              <div className="text-xs text-gray-400">Quantity / Shares</div>
            </div>
            <div className="text-center p-2 rounded-lg bg-bg-secondary border border-bg-border">
              <div className="text-lg font-bold text-white font-mono">
                {formatCurrency(result.positionValue, 'INR', true)}
              </div>
              <div className="text-xs text-gray-400">Position Value</div>
            </div>
            <div className="text-center p-2 rounded-lg bg-red-500/10 border border-red-500/20">
              <div className="text-lg font-bold text-red-400 font-mono">
                {formatCurrency(result.riskAmount, 'INR', true)}
              </div>
              <div className="text-xs text-gray-400">Max Risk</div>
            </div>
            {result.rewardAmount > 0 && (
              <div className="text-center p-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20">
                <div className="text-lg font-bold text-emerald-400 font-mono">
                  {formatCurrency(result.rewardAmount, 'INR', true)}
                </div>
                <div className="text-xs text-gray-400">
                  Reward ({(result.rewardPercent * 100).toFixed(2)}%)
                </div>
              </div>
            )}
          </div>
          {result.rewardAmount > 0 && (
            <div className="mt-2 text-center text-sm text-gray-400">
              R:R ={' '}
              <span className="text-white font-bold font-mono">
                1:{(result.rewardAmount / result.riskAmount).toFixed(2)}
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ─── VaR Display ──────────────────────────────────────────────────────────────

function VaRCard({ metrics }: { metrics: RiskMetrics }) {
  return (
    <div className="rounded-xl bg-bg-card border border-bg-border p-4">
      <div className="flex items-center gap-2 mb-4">
        <Shield size={16} className="text-accent-blue" />
        <h3 className="font-semibold text-white text-sm">Portfolio Risk Metrics</h3>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-lg bg-red-500/10 border border-red-500/20 p-3 text-center">
          <div className="text-xs text-gray-400 mb-1">VaR (95%)</div>
          <div className="text-xl font-bold text-red-400 font-mono">
            {formatPercent(metrics.portfolioVaR95)}
          </div>
          <div className="text-xs text-gray-500">1-day at 95% confidence</div>
        </div>
        <div className="rounded-lg bg-red-700/10 border border-red-700/20 p-3 text-center">
          <div className="text-xs text-gray-400 mb-1">VaR (99%)</div>
          <div className="text-xl font-bold text-red-600 font-mono">
            {formatPercent(metrics.portfolioVaR99)}
          </div>
          <div className="text-xs text-gray-500">1-day at 99% confidence</div>
        </div>
        <div className="rounded-lg bg-orange-500/10 border border-orange-500/20 p-3 text-center">
          <div className="text-xs text-gray-400 mb-1">Max Drawdown</div>
          <div className="text-xl font-bold text-orange-400 font-mono">
            {formatPercent(-Math.abs(metrics.maxDrawdown))}
          </div>
          <div className="text-xs text-gray-500">Historical peak-to-trough</div>
        </div>
        <div className="rounded-lg bg-blue-500/10 border border-blue-500/20 p-3 text-center">
          <div className="text-xs text-gray-400 mb-1">Sharpe Ratio</div>
          <div
            className={clsx(
              'text-xl font-bold font-mono',
              metrics.sharpeRatio > 1.5
                ? 'text-emerald-400'
                : metrics.sharpeRatio > 1
                ? 'text-yellow-400'
                : 'text-red-400'
            )}
          >
            {metrics.sharpeRatio.toFixed(2)}
          </div>
          <div className="text-xs text-gray-500">Risk-adjusted returns</div>
        </div>
      </div>

      <div className="mt-3 grid grid-cols-3 gap-2">
        <div className="text-center">
          <div className="text-xs text-gray-500">Beta</div>
          <div className="text-sm font-bold font-mono text-white">{metrics.beta.toFixed(2)}</div>
        </div>
        <div className="text-center">
          <div className="text-xs text-gray-500">Alpha</div>
          <div
            className={clsx(
              'text-sm font-bold font-mono',
              metrics.alpha >= 0 ? 'text-emerald-400' : 'text-red-400'
            )}
          >
            {formatPercent(metrics.alpha)}
          </div>
        </div>
        <div className="text-center">
          <div className="text-xs text-gray-500">Volatility</div>
          <div className="text-sm font-bold font-mono text-white">
            {formatPercent(metrics.volatility)}
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Risk Meters ──────────────────────────────────────────────────────────────

function RiskMeter({ label, value, icon }: {
  label: string;
  value: number;
  icon: React.ReactNode;
}) {
  const color =
    value >= 75 ? '#ef4444' : value >= 50 ? '#f97316' : value >= 25 ? '#eab308' : '#10b981';

  return (
    <div className="rounded-xl bg-bg-card border border-bg-border p-4">
      <div className="flex items-center gap-2 mb-3">
        {icon}
        <span className="text-sm font-semibold text-white">{label}</span>
        <span className="ml-auto text-lg font-bold font-mono" style={{ color }}>
          {value.toFixed(0)}
        </span>
      </div>
      <div className="h-2 bg-gray-700 rounded-full overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-700"
          style={{ width: `${value}%`, background: color }}
        />
      </div>
      <div className="mt-1 flex justify-between text-xs text-gray-600">
        <span>Low</span>
        <span>High</span>
      </div>
    </div>
  );
}

// ─── Drawdown Chart ───────────────────────────────────────────────────────────

function DrawdownChart() {
  const mockData = Array.from({ length: 90 }, (_, i) => {
    const t = i / 90;
    const wave = Math.sin(t * Math.PI * 4) * 5;
    const trend = -t * 8;
    return {
      date: new Date(Date.now() - (90 - i) * 86400000).toLocaleDateString('en-IN', {
        month: 'short',
        day: '2-digit',
      }),
      drawdown: Math.min(0, wave + trend + Math.random() * 2 - 1),
    };
  });

  return (
    <div className="rounded-xl bg-bg-card border border-bg-border p-4">
      <div className="flex items-center gap-2 mb-3">
        <TrendingDown size={16} className="text-red-400" />
        <h3 className="font-semibold text-white text-sm">Portfolio Drawdown (90d)</h3>
      </div>
      <ResponsiveContainer width="100%" height={150}>
        <ComposedChart data={mockData} margin={{ top: 5, right: 10, bottom: 0, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#1c2333" vertical={false} />
          <XAxis dataKey="date" tick={{ fill: '#6b7280', fontSize: 10 }} axisLine={false} tickLine={false} interval={14} />
          <YAxis
            tick={{ fill: '#6b7280', fontSize: 10 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => `${v.toFixed(0)}%`}
          />
          <ReferenceLine y={0} stroke="#4b5563" strokeWidth={1} />
          <Tooltip
            contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', borderRadius: 8, fontSize: 11 }}
            formatter={(val: number) => [`${val.toFixed(2)}%`, 'Drawdown']}
          />
          <Area
            type="monotone"
            dataKey="drawdown"
            stroke="#ef4444"
            fill="#ef444420"
            strokeWidth={1.5}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

// ─── Main RiskDashboard ───────────────────────────────────────────────────────

export const RiskDashboard: React.FC = () => {
  const riskMetrics = useTradingStore((s) => s.riskMetrics);

  const mockMetrics: RiskMetrics = riskMetrics || {
    portfolioVaR95: -2.1,
    portfolioVaR99: -3.5,
    expectedShortfall: -4.2,
    maxDrawdown: -12.4,
    currentDrawdown: -3.1,
    sharpeRatio: 1.42,
    beta: 0.87,
    alpha: 2.1,
    volatility: 14.2,
    correlationToNifty: 0.78,
    concentrationRisk: 42,
    overnightGapRisk: 28,
    globalShockRisk: 35,
  };

  return (
    <div className="space-y-4">
      {/* Alerts Row */}
      <div className="rounded-xl bg-yellow-500/10 border border-yellow-500/30 p-3 flex items-start gap-3">
        <AlertTriangle size={16} className="text-yellow-400 mt-0.5 shrink-0" />
        <div className="text-sm text-yellow-200">
          <span className="font-semibold">Risk Notice:</span> Current market conditions show
          elevated overnight gap risk. Consider reducing positional exposure and increasing hedge
          coverage. Global macro events may impact positions.
        </div>
      </div>

      {/* VaR + Position Size */}
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        <VaRCard metrics={mockMetrics} />
        <PositionSizeCalculator />
      </div>

      {/* Risk Meters */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <RiskMeter
          label="Overnight Gap Risk"
          value={mockMetrics.overnightGapRisk}
          icon={<Zap size={14} className="text-yellow-400" />}
        />
        <RiskMeter
          label="Concentration Risk"
          value={mockMetrics.concentrationRisk}
          icon={<AlertTriangle size={14} className="text-orange-400" />}
        />
        <RiskMeter
          label="Global Shock Risk"
          value={mockMetrics.globalShockRisk}
          icon={<Shield size={14} className="text-red-400" />}
        />
      </div>

      {/* Drawdown Chart */}
      <DrawdownChart />
    </div>
  );
};

export default RiskDashboard;
