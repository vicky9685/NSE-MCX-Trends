import React, { useMemo, useState } from 'react';
import {
  ComposedChart,
  Bar,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  ReferenceLine,
  Legend,
} from 'recharts';
import { clsx } from 'clsx';
import type { OHLCV } from '@/types';

interface CandlestickChartProps {
  data: OHLCV[];
  symbol: string;
  interval?: string;
  showEMA?: boolean;
  showBB?: boolean;
  height?: number;
}

type Interval = '1m' | '5m' | '15m' | '30m' | '1h' | '1d' | '1w';

const INTERVAL_LABELS: Record<Interval, string> = {
  '1m': '1 Min',
  '5m': '5 Min',
  '15m': '15 Min',
  '30m': '30 Min',
  '1h': '1 Hour',
  '1d': '1 Day',
  '1w': '1 Week',
};

// ─── EMA Calculation ──────────────────────────────────────────────────────────

function calcEMA(data: number[], period: number): (number | null)[] {
  const k = 2 / (period + 1);
  const result: (number | null)[] = [];
  let ema: number | null = null;

  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      result.push(null);
    } else if (i === period - 1) {
      ema = data.slice(0, period).reduce((a, b) => a + b, 0) / period;
      result.push(Number(ema.toFixed(2)));
    } else {
      ema = data[i] * k + ema! * (1 - k);
      result.push(Number(ema.toFixed(2)));
    }
  }
  return result;
}

// ─── Bollinger Bands ──────────────────────────────────────────────────────────

function calcBB(data: number[], period: number = 20, multiplier: number = 2) {
  return data.map((_, i) => {
    if (i < period - 1) return { upper: null, middle: null, lower: null };
    const slice = data.slice(i - period + 1, i + 1);
    const mean = slice.reduce((a, b) => a + b, 0) / period;
    const variance = slice.reduce((a, b) => a + (b - mean) ** 2, 0) / period;
    const std = Math.sqrt(variance);
    return {
      upper: Number((mean + multiplier * std).toFixed(2)),
      middle: Number(mean.toFixed(2)),
      lower: Number((mean - multiplier * std).toFixed(2)),
    };
  });
}

// ─── RSI Calculation ──────────────────────────────────────────────────────────

function calcRSI(data: number[], period: number = 14): (number | null)[] {
  const result: (number | null)[] = [];
  const gains: number[] = [];
  const losses: number[] = [];

  for (let i = 1; i < data.length; i++) {
    const diff = data[i] - data[i - 1];
    gains.push(Math.max(diff, 0));
    losses.push(Math.max(-diff, 0));
  }

  result.push(null);

  for (let i = 0; i < gains.length; i++) {
    if (i < period - 1) {
      result.push(null);
    } else if (i === period - 1) {
      const avgGain = gains.slice(0, period).reduce((a, b) => a + b, 0) / period;
      const avgLoss = losses.slice(0, period).reduce((a, b) => a + b, 0) / period;
      const rs = avgLoss === 0 ? 100 : avgGain / avgLoss;
      result.push(Number((100 - 100 / (1 + rs)).toFixed(2)));
    } else {
      const prevAvgGain =
        ((result[result.length - 1] ?? 50) / 100) * (period - 1) * gains[i - 1] || 0;
      const avgGain = (prevAvgGain * (period - 1) + gains[i]) / period;
      const prevAvgLoss =
        ((100 - (result[result.length - 1] ?? 50)) / 100) * (period - 1) * losses[i - 1] || 0;
      const avgLoss = (prevAvgLoss * (period - 1) + losses[i]) / period;
      const rs = avgLoss === 0 ? 100 : avgGain / avgLoss;
      result.push(Number((100 - 100 / (1 + rs)).toFixed(2)));
    }
  }

  return result;
}

// ─── MACD Calculation ─────────────────────────────────────────────────────────

function calcMACD(data: number[]) {
  const ema12 = calcEMA(data, 12);
  const ema26 = calcEMA(data, 26);
  const macd = ema12.map((v, i) =>
    v !== null && ema26[i] !== null ? Number((v - ema26[i]!).toFixed(2)) : null
  );
  const macdValues = macd.filter((v): v is number => v !== null);
  const signalRaw = calcEMA(macdValues, 9);
  const signal: (number | null)[] = macd.map((v) => (v === null ? null : null));
  let signalIdx = 0;
  for (let i = 0; i < macd.length; i++) {
    if (macd[i] !== null) {
      signal[i] = signalRaw[signalIdx++] ?? null;
    }
  }
  const histogram = macd.map((v, i) =>
    v !== null && signal[i] !== null ? Number((v - signal[i]!).toFixed(2)) : null
  );
  return { macd, signal, histogram };
}

// ─── Candle Bar Shape ─────────────────────────────────────────────────────────

const CandleBar = (props: {
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  open?: number;
  close?: number;
  high?: number;
  low?: number;
  value?: [number, number];
  payload?: { open: number; close: number; high: number; low: number };
}) => {
  const { x = 0, width = 0, payload } = props;
  if (!payload) return null;

  const { open, close, high, low } = payload;
  const isUp = close >= open;
  const color = isUp ? '#10b981' : '#ef4444';
  const cx = x + width / 2;

  // We need the chart's y-scale to draw properly
  // This is a simplified version using relative positions
  return <g />;
};

// ─── Custom Tooltip ───────────────────────────────────────────────────────────

const CustomTooltip = ({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: Array<{ value: number; name: string; color: string }>;
  label?: string;
}) => {
  if (!active || !payload?.length) return null;

  const candle = payload[0]?.payload as {
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
    timestamp: string;
  };

  if (!candle) return null;

  return (
    <div className="bg-bg-card border border-bg-border rounded-lg p-3 shadow-card text-xs font-mono">
      <div className="text-gray-400 mb-2">{new Date(candle.timestamp).toLocaleString('en-IN')}</div>
      <div className="grid grid-cols-2 gap-x-4 gap-y-1">
        <span className="text-gray-400">O:</span>
        <span className="text-white">{candle.open?.toFixed(2)}</span>
        <span className="text-gray-400">H:</span>
        <span className="text-emerald-400">{candle.high?.toFixed(2)}</span>
        <span className="text-gray-400">L:</span>
        <span className="text-red-400">{candle.low?.toFixed(2)}</span>
        <span className="text-gray-400">C:</span>
        <span className={candle.close >= candle.open ? 'text-emerald-400' : 'text-red-400'}>
          {candle.close?.toFixed(2)}
        </span>
        <span className="text-gray-400">V:</span>
        <span className="text-blue-400">
          {(candle.volume / 1000).toFixed(1)}K
        </span>
      </div>
      {payload.filter((p) => p.name !== 'candle').map((p) => (
        <div key={p.name} className="mt-1 flex justify-between gap-3">
          <span style={{ color: p.color }}>{p.name}:</span>
          <span style={{ color: p.color }}>{p.value?.toFixed(2)}</span>
        </div>
      ))}
    </div>
  );
};

// ─── Main Chart ───────────────────────────────────────────────────────────────

export const CandlestickChart: React.FC<CandlestickChartProps> = ({
  data,
  symbol,
  interval = '1d',
  showEMA = true,
  showBB = true,
  height = 400,
}) => {
  const [activeInterval, setActiveInterval] = useState<Interval>(interval as Interval);
  const [showRSI, setShowRSI] = useState(true);
  const [showMACD, setShowMACD] = useState(false);

  const enriched = useMemo(() => {
    if (!data || data.length === 0) return [];

    const closes = data.map((d) => d.close);
    const ema20 = calcEMA(closes, 20);
    const ema50 = calcEMA(closes, 50);
    const ema200 = calcEMA(closes, 200);
    const bb = calcBB(closes, 20, 2);
    const rsi = calcRSI(closes, 14);
    const { macd, signal, histogram } = calcMACD(closes);

    return data.map((d, i) => ({
      ...d,
      ema20: ema20[i],
      ema50: ema50[i],
      ema200: ema200[i],
      bbUpper: bb[i].upper,
      bbMiddle: bb[i].middle,
      bbLower: bb[i].lower,
      rsi: rsi[i],
      macd: macd[i],
      macdSignal: signal[i],
      macdHistogram: histogram[i],
      // For bar chart representation of candles
      candleBody: [Math.min(d.open, d.close), Math.max(d.open, d.close)] as [number, number],
      candleColor: d.close >= d.open ? '#10b981' : '#ef4444',
      isUp: d.close >= d.open,
      displayDate: new Date(d.timestamp).toLocaleDateString('en-IN', {
        month: 'short',
        day: '2-digit',
      }),
    }));
  }, [data]);

  const yDomain = useMemo((): [number, number] => {
    if (!enriched.length) return [0, 100];
    const lows = enriched.map((d) => d.low);
    const highs = enriched.map((d) => d.high);
    const min = Math.min(...lows);
    const max = Math.max(...highs);
    const pad = (max - min) * 0.05;
    return [min - pad, max + pad];
  }, [enriched]);

  const volMax = useMemo(() => {
    if (!enriched.length) return 1;
    return Math.max(...enriched.map((d) => d.volume));
  }, [enriched]);

  if (!data || data.length === 0) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-500">
        No chart data available
      </div>
    );
  }

  return (
    <div className="rounded-xl bg-bg-card border border-bg-border overflow-hidden">
      {/* Chart Header */}
      <div className="px-4 py-3 border-b border-bg-border flex items-center gap-3 flex-wrap">
        <span className="font-bold text-white font-mono">{symbol}</span>
        <div className="flex items-center gap-1">
          {(Object.keys(INTERVAL_LABELS) as Interval[]).map((iv) => (
            <button
              key={iv}
              onClick={() => setActiveInterval(iv)}
              className={clsx(
                'text-xs px-2 py-1 rounded transition-colors',
                activeInterval === iv
                  ? 'bg-accent-blue text-white'
                  : 'text-gray-400 hover:text-white hover:bg-bg-hover'
              )}
            >
              {INTERVAL_LABELS[iv]}
            </button>
          ))}
        </div>
        <div className="ml-auto flex items-center gap-2">
          <button
            onClick={() => setShowRSI((v) => !v)}
            className={clsx(
              'text-xs px-2 py-1 rounded transition-colors',
              showRSI ? 'bg-purple-500/20 text-purple-400' : 'text-gray-500 hover:text-gray-300'
            )}
          >
            RSI
          </button>
          <button
            onClick={() => setShowMACD((v) => !v)}
            className={clsx(
              'text-xs px-2 py-1 rounded transition-colors',
              showMACD ? 'bg-blue-500/20 text-blue-400' : 'text-gray-500 hover:text-gray-300'
            )}
          >
            MACD
          </button>
        </div>
      </div>

      {/* Main Price Chart */}
      <div className="px-2 pt-4">
        <ResponsiveContainer width="100%" height={height}>
          <ComposedChart data={enriched} margin={{ top: 5, right: 40, bottom: 5, left: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1c2333" vertical={false} />
            <XAxis
              dataKey="displayDate"
              tick={{ fill: '#6b7280', fontSize: 10 }}
              axisLine={false}
              tickLine={false}
              interval="preserveStartEnd"
            />
            <YAxis
              domain={yDomain}
              tick={{ fill: '#6b7280', fontSize: 10 }}
              axisLine={false}
              tickLine={false}
              orientation="right"
              tickFormatter={(v) => v.toFixed(0)}
            />
            <Tooltip content={<CustomTooltip />} />

            {/* Bollinger Bands */}
            {showBB && (
              <>
                <Line dataKey="bbUpper" stroke="#6b7280" strokeDasharray="4 2" dot={false} strokeWidth={1} name="BB Upper" />
                <Line dataKey="bbMiddle" stroke="#4b5563" strokeDasharray="4 2" dot={false} strokeWidth={1} name="BB Mid" />
                <Line dataKey="bbLower" stroke="#6b7280" strokeDasharray="4 2" dot={false} strokeWidth={1} name="BB Lower" />
              </>
            )}

            {/* EMA Lines */}
            {showEMA && (
              <>
                <Line dataKey="ema20" stroke="#f59e0b" dot={false} strokeWidth={1.5} name="EMA 20" />
                <Line dataKey="ema50" stroke="#8b5cf6" dot={false} strokeWidth={1.5} name="EMA 50" />
                <Line dataKey="ema200" stroke="#ec4899" dot={false} strokeWidth={1.5} name="EMA 200" />
              </>
            )}

            {/* Closing price line (acts as candlestick approximation) */}
            <Line
              dataKey="close"
              stroke="#3b82f6"
              dot={false}
              strokeWidth={1.5}
              name="Close"
            />

            <Legend
              wrapperStyle={{ fontSize: 11, paddingTop: 4 }}
              formatter={(value) => <span style={{ color: '#9ca3af' }}>{value}</span>}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* Volume Chart */}
      <div className="px-2 pb-1">
        <ResponsiveContainer width="100%" height={60}>
          <ComposedChart data={enriched} margin={{ top: 0, right: 40, bottom: 0, left: 5 }}>
            <YAxis hide domain={[0, volMax * 2]} />
            <XAxis hide />
            <Bar dataKey="volume" name="Volume" radius={[1, 1, 0, 0]}>
              {enriched.map((entry, index) => (
                <rect key={index} fill={entry.isUp ? '#10b98150' : '#ef444450'} />
              ))}
            </Bar>
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* RSI Panel */}
      {showRSI && (
        <div className="px-2 border-t border-bg-border">
          <div className="text-xs text-gray-500 px-2 pt-2">RSI (14)</div>
          <ResponsiveContainer width="100%" height={80}>
            <ComposedChart data={enriched} margin={{ top: 5, right: 40, bottom: 5, left: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1c2333" vertical={false} />
              <XAxis hide />
              <YAxis
                domain={[0, 100]}
                tick={{ fill: '#6b7280', fontSize: 9 }}
                axisLine={false}
                tickLine={false}
                orientation="right"
                ticks={[30, 50, 70]}
              />
              <ReferenceLine y={70} stroke="#ef4444" strokeDasharray="3 3" strokeWidth={1} />
              <ReferenceLine y={30} stroke="#10b981" strokeDasharray="3 3" strokeWidth={1} />
              <ReferenceLine y={50} stroke="#4b5563" strokeDasharray="2 4" strokeWidth={1} />
              <Line
                dataKey="rsi"
                stroke="#a78bfa"
                dot={false}
                strokeWidth={1.5}
                name="RSI"
                connectNulls
              />
              <Tooltip
                contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', borderRadius: 8, fontSize: 11 }}
                formatter={(val: number) => [val?.toFixed(2), 'RSI']}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* MACD Panel */}
      {showMACD && (
        <div className="px-2 border-t border-bg-border">
          <div className="text-xs text-gray-500 px-2 pt-2">MACD (12,26,9)</div>
          <ResponsiveContainer width="100%" height={80}>
            <ComposedChart data={enriched} margin={{ top: 5, right: 40, bottom: 5, left: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1c2333" vertical={false} />
              <XAxis hide />
              <YAxis
                tick={{ fill: '#6b7280', fontSize: 9 }}
                axisLine={false}
                tickLine={false}
                orientation="right"
                tickFormatter={(v) => v.toFixed(1)}
              />
              <ReferenceLine y={0} stroke="#4b5563" strokeWidth={1} />
              <Bar dataKey="macdHistogram" name="Histogram" radius={[1, 1, 0, 0]}>
                {enriched.map((entry, index) => (
                  <rect
                    key={index}
                    fill={(entry.macdHistogram ?? 0) >= 0 ? '#10b98160' : '#ef444460'}
                  />
                ))}
              </Bar>
              <Line dataKey="macd" stroke="#3b82f6" dot={false} strokeWidth={1.5} name="MACD" connectNulls />
              <Line dataKey="macdSignal" stroke="#f59e0b" dot={false} strokeWidth={1.5} name="Signal" connectNulls />
              <Tooltip
                contentStyle={{ background: '#1e2433', border: '1px solid #2d3748', borderRadius: 8, fontSize: 11 }}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
};

export default CandlestickChart;
