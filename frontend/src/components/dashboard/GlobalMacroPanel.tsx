import React from 'react';
import { TrendingUp, TrendingDown, Minus, Globe, DollarSign, Zap } from 'lucide-react';
import { clsx } from 'clsx';
import type { GlobalMacroData, IndexQuote } from '@/types';
import { formatPercent } from '@/utils/formatters';

interface GlobalMacroPanelProps {
  data: GlobalMacroData | null;
}

function IndexRow({ quote, compact = false }: { quote: IndexQuote; compact?: boolean }) {
  const isPositive = quote.changePercent > 0;
  const isNegative = quote.changePercent < 0;

  return (
    <div className="flex items-center justify-between py-1.5 border-b border-gray-800/50 last:border-0">
      <span className={clsx('text-xs font-medium', compact ? 'text-gray-400' : 'text-gray-300')}>
        {quote.name}
      </span>
      <div className="flex items-center gap-2">
        <span className="text-xs font-mono font-semibold text-white">
          {quote.value.toLocaleString('en-US', { maximumFractionDigits: 2 })}
        </span>
        <span
          className={clsx(
            'text-xs font-mono font-bold flex items-center gap-0.5',
            isPositive ? 'text-emerald-400' : isNegative ? 'text-red-400' : 'text-gray-400'
          )}
        >
          {isPositive ? (
            <TrendingUp size={10} />
          ) : isNegative ? (
            <TrendingDown size={10} />
          ) : (
            <Minus size={10} />
          )}
          {formatPercent(quote.changePercent)}
        </span>
      </div>
    </div>
  );
}

function PolicyBadge({ policy }: { policy: 'HAWKISH' | 'NEUTRAL' | 'DOVISH' }) {
  return (
    <span
      className={clsx(
        'text-xs px-2 py-0.5 rounded-full font-semibold',
        policy === 'HAWKISH'
          ? 'bg-red-500/20 text-red-400'
          : policy === 'DOVISH'
          ? 'bg-emerald-500/20 text-emerald-400'
          : 'bg-yellow-500/20 text-yellow-400'
      )}
    >
      {policy}
    </span>
  );
}

export const GlobalMacroPanel: React.FC<GlobalMacroPanelProps> = ({ data }) => {
  if (!data) {
    return (
      <div className="rounded-xl bg-bg-card border border-bg-border p-4">
        <div className="text-gray-500 text-sm">Loading global macro data...</div>
      </div>
    );
  }

  return (
    <div className="rounded-xl bg-bg-card border border-bg-border overflow-hidden">
      <div className="px-4 py-3 border-b border-bg-border flex items-center gap-2">
        <Globe size={16} className="text-accent-blue" />
        <h3 className="font-semibold text-white text-sm">Global Macro</h3>
        <span className="ml-auto text-xs text-gray-500">
          {new Date(data.timestamp).toLocaleTimeString('en-IN', { timeZone: 'Asia/Kolkata' })} IST
        </span>
      </div>

      <div className="p-4 grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* US Indices */}
        <div>
          <div className="flex items-center gap-1 mb-2">
            <span className="text-xs font-semibold text-gray-400 uppercase tracking-wide">🇺🇸 US Indices</span>
          </div>
          <IndexRow quote={data.usIndices.dow} />
          <IndexRow quote={data.usIndices.sp500} />
          <IndexRow quote={data.usIndices.nasdaq} />
          <div className="mt-2 flex items-center justify-between py-1.5">
            <span className="text-xs text-gray-400">VIX</span>
            <span
              className={clsx(
                'text-xs font-mono font-bold',
                data.usIndices.vix.value > 20 ? 'text-red-400' : 'text-emerald-400'
              )}
            >
              {data.usIndices.vix.value.toFixed(2)}
              <span className="ml-1 text-gray-500">
                ({formatPercent(data.usIndices.vix.changePercent)})
              </span>
            </span>
          </div>

          {/* European Indices */}
          <div className="mt-3">
            <div className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">🇪🇺 Europe</div>
            <IndexRow quote={data.europeanIndices.ftse} />
            <IndexRow quote={data.europeanIndices.dax} />
            <IndexRow quote={data.europeanIndices.cac} />
          </div>
        </div>

        {/* Asian Indices */}
        <div>
          <div className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">🌏 Asia</div>
          <IndexRow quote={data.asianIndices.nikkei} />
          <IndexRow quote={data.asianIndices.hangSeng} />
          <IndexRow quote={data.asianIndices.sgxNifty} />
          <IndexRow quote={data.asianIndices.shanghai} />

          {/* Currencies */}
          <div className="mt-3">
            <div className="flex items-center gap-1 mb-2">
              <DollarSign size={12} className="text-gray-400" />
              <span className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Currencies</span>
            </div>
            {[
              { name: 'USD/INR', value: data.currencies.usdInr, change: data.currencies.usdInrChange },
              { name: 'DXY', value: data.currencies.dxy, change: data.currencies.dxyChange },
              { name: 'EUR/USD', value: data.currencies.eurusd, change: 0 },
              { name: 'USD/JPY', value: data.currencies.usdjpy, change: 0 },
            ].map((c) => (
              <div key={c.name} className="flex items-center justify-between py-1.5 border-b border-gray-800/50 last:border-0">
                <span className="text-xs text-gray-400">{c.name}</span>
                <div className="flex items-center gap-2">
                  <span className="text-xs font-mono font-semibold text-white">
                    {c.value.toFixed(c.name === 'USD/INR' ? 4 : 2)}
                  </span>
                  {c.change !== 0 && (
                    <span
                      className={clsx(
                        'text-xs font-mono',
                        c.change > 0 ? 'text-emerald-400' : 'text-red-400'
                      )}
                    >
                      {c.change > 0 ? '+' : ''}
                      {c.change.toFixed(4)}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Commodities + Bonds + Policy */}
        <div>
          <div className="flex items-center gap-1 mb-2">
            <Zap size={12} className="text-yellow-400" />
            <span className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Commodities</span>
          </div>
          {Object.entries(data.commodities).map(([key, c]) => (
            <div key={key} className="flex items-center justify-between py-1.5 border-b border-gray-800/50 last:border-0">
              <span className="text-xs text-gray-400">{c.name}</span>
              <div className="text-right">
                <div className="text-xs font-mono font-semibold text-white">
                  {c.value.toFixed(2)} <span className="text-gray-600 text-xs">{c.unit}</span>
                </div>
                <div
                  className={clsx(
                    'text-xs font-mono',
                    c.changePercent > 0 ? 'text-emerald-400' : 'text-red-400'
                  )}
                >
                  {formatPercent(c.changePercent)}
                </div>
              </div>
            </div>
          ))}

          {/* Bonds */}
          <div className="mt-3">
            <div className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-2">Bond Yields</div>
            <div className="flex items-center justify-between py-1.5 border-b border-gray-800/50">
              <span className="text-xs text-gray-400">US 10Y</span>
              <div className="text-right">
                <span className="text-xs font-mono font-semibold text-white">
                  {data.bonds.us10y.toFixed(3)}%
                </span>
                <span
                  className={clsx(
                    'ml-1 text-xs font-mono',
                    data.bonds.us10yChange > 0 ? 'text-red-400' : 'text-emerald-400'
                  )}
                >
                  {data.bonds.us10yChange > 0 ? '+' : ''}{data.bonds.us10yChange.toFixed(3)}
                </span>
              </div>
            </div>
            <div className="flex items-center justify-between py-1.5 border-b border-gray-800/50">
              <span className="text-xs text-gray-400">US 2Y</span>
              <span className="text-xs font-mono font-semibold text-white">
                {data.bonds.us2y.toFixed(3)}%
              </span>
            </div>
            <div className="flex items-center justify-between py-1.5">
              <span className="text-xs text-gray-400">IN 10Y</span>
              <span className="text-xs font-mono font-semibold text-white">
                {data.bonds.in10y.toFixed(3)}%
              </span>
            </div>
          </div>

          {/* Policy */}
          <div className="mt-3 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-gray-400">Fed Policy</span>
              <PolicyBadge policy={data.fedPolicy} />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-gray-400">RBI Policy</span>
              <PolicyBadge policy={data.rbiPolicy} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default GlobalMacroPanel;
