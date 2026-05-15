import React, { useMemo } from 'react';
import { clsx } from 'clsx';
import type { OptionChain, OptionStrike } from '@/types';
import { formatNumber } from '@/utils/formatters';

interface OptionChainHeatmapProps {
  chain: OptionChain | null;
  onStrikeSelect?: (strike: number) => void;
  selectedStrike?: number | null;
}

function oiIntensity(oi: number, maxOI: number): number {
  if (maxOI === 0) return 0;
  return Math.min(oi / maxOI, 1);
}

function OICell({
  oi,
  maxOI,
  type,
  change,
}: {
  oi: number;
  maxOI: number;
  type: 'CE' | 'PE';
  change: number;
}) {
  const intensity = oiIntensity(oi, maxOI);
  const baseColor = type === 'CE' ? `rgba(239,68,68,` : `rgba(16,185,129,`;
  const bg = `${baseColor}${0.05 + intensity * 0.35})`;
  const isPositive = change >= 0;

  return (
    <div
      className="text-right text-xs font-mono transition-all duration-500"
      style={{ background: bg, padding: '4px 6px' }}
    >
      <div className="font-semibold text-white">{formatNumber(oi, 0)}</div>
      <div className={clsx('text-xs', isPositive ? 'text-emerald-400' : 'text-red-400')}>
        {isPositive ? '+' : ''}{change.toFixed(0)}
      </div>
    </div>
  );
}

function NumCell({ value, decimals = 2, colorize = false }: {
  value: number;
  decimals?: number;
  colorize?: boolean;
}) {
  return (
    <div
      className={clsx(
        'text-right text-xs font-mono px-1.5 py-1',
        colorize && value >= 0 ? 'text-emerald-400' : colorize && value < 0 ? 'text-red-400' : 'text-gray-300'
      )}
    >
      {value.toFixed(decimals)}
    </div>
  );
}

export const OptionChainHeatmap: React.FC<OptionChainHeatmapProps> = ({
  chain,
  onStrikeSelect,
  selectedStrike,
}) => {
  const maxCallOI = useMemo(
    () => (chain ? Math.max(...chain.strikes.map((s) => s.callOI), 1) : 1),
    [chain]
  );
  const maxPutOI = useMemo(
    () => (chain ? Math.max(...chain.strikes.map((s) => s.putOI), 1) : 1),
    [chain]
  );

  if (!chain) {
    return (
      <div className="flex items-center justify-center h-48 text-gray-500 text-sm">
        Select a symbol to view the option chain
      </div>
    );
  }

  const underlyingPrice = chain.underlyingPrice;

  return (
    <div className="rounded-xl bg-bg-card border border-bg-border overflow-hidden">
      {/* Header Info */}
      <div className="px-4 py-3 border-b border-bg-border bg-bg-secondary flex items-center gap-4 flex-wrap">
        <div>
          <span className="text-sm font-bold text-white font-mono">{chain.underlyingSymbol}</span>
          <span className="ml-2 text-sm font-mono text-gray-400">
            {underlyingPrice.toFixed(2)}
          </span>
        </div>
        <div className="text-xs text-gray-400">Expiry: <span className="text-white">{chain.expiryDate}</span></div>
        <div className="text-xs text-gray-400">
          PCR:{' '}
          <span
            className={clsx(
              'font-bold font-mono',
              chain.pcr > 1.2 ? 'text-emerald-400' : chain.pcr < 0.8 ? 'text-red-400' : 'text-yellow-400'
            )}
          >
            {chain.pcr.toFixed(2)}
          </span>
        </div>
        <div className="text-xs text-gray-400">
          Max Pain:{' '}
          <span className="text-yellow-400 font-bold font-mono">{chain.maxPainStrike}</span>
        </div>
        <div className="text-xs text-gray-400">
          Total CE OI: <span className="text-red-400 font-mono">{formatNumber(chain.totalCallOI, 0)}</span>
        </div>
        <div className="text-xs text-gray-400">
          Total PE OI: <span className="text-emerald-400 font-mono">{formatNumber(chain.totalPutOI, 0)}</span>
        </div>
        <div className="text-xs text-gray-400">
          IV Rank: <span className="text-blue-400 font-mono">{chain.indiaVix?.toFixed(2)}</span>
        </div>
      </div>

      {/* Column Headers */}
      <div className="overflow-x-auto">
        <table className="w-full text-xs border-collapse">
          <thead>
            <tr className="bg-bg-tertiary border-b border-bg-border">
              {/* CE Side */}
              <th className="text-center text-red-400 font-semibold py-2 px-1 border-r border-bg-border" colSpan={4}>
                CALLS (CE)
              </th>
              {/* Strike */}
              <th className="text-center text-yellow-400 font-semibold py-2 px-3 bg-bg-secondary border-x border-yellow-500/30 min-w-[80px]">
                STRIKE
              </th>
              {/* PE Side */}
              <th className="text-center text-emerald-400 font-semibold py-2 px-1 border-l border-bg-border" colSpan={4}>
                PUTS (PE)
              </th>
            </tr>
            <tr className="bg-bg-secondary border-b border-bg-border text-gray-400">
              <th className="text-right py-1 px-1.5">OI</th>
              <th className="text-right py-1 px-1.5">IV</th>
              <th className="text-right py-1 px-1.5">LTP</th>
              <th className="text-right py-1 px-1.5 border-r border-bg-border">Chg%</th>

              <th className="text-center py-1 px-3 bg-bg-secondary border-x border-yellow-500/20"></th>

              <th className="text-right py-1 px-1.5">OI</th>
              <th className="text-right py-1 px-1.5">IV</th>
              <th className="text-right py-1 px-1.5">LTP</th>
              <th className="text-right py-1 px-1.5">Chg%</th>
            </tr>
          </thead>
          <tbody>
            {chain.strikes.map((strike) => {
              const isATM = strike.isAtTheMoney;
              const isMaxPain = strike.isMaxPain;
              const isSelected = selectedStrike === strike.strikePrice;
              const isITM_CE = strike.strikePrice < underlyingPrice;
              const isITM_PE = strike.strikePrice > underlyingPrice;

              return (
                <tr
                  key={strike.strikePrice}
                  onClick={() => onStrikeSelect?.(strike.strikePrice)}
                  className={clsx(
                    'border-b border-bg-border/50 cursor-pointer transition-all duration-150',
                    isSelected ? 'bg-accent-blue/10' : 'hover:bg-bg-hover',
                    isATM ? 'bg-yellow-500/5' : ''
                  )}
                >
                  {/* CE OI */}
                  <td className={clsx(isITM_CE ? 'bg-red-500/5' : '')}>
                    <OICell
                      oi={strike.callOI}
                      maxOI={maxCallOI}
                      type="CE"
                      change={strike.callOIChange}
                    />
                  </td>

                  {/* CE IV */}
                  <td className="text-right px-1.5 py-1">
                    <span className="text-orange-400 font-mono">{strike.callIV.toFixed(1)}%</span>
                  </td>

                  {/* CE LTP */}
                  <td className="text-right px-1.5 py-1">
                    <span className="text-white font-mono">{strike.callLTP.toFixed(2)}</span>
                  </td>

                  {/* CE Change% */}
                  <td className="text-right px-1.5 py-1 border-r border-bg-border">
                    <span
                      className={clsx(
                        'font-mono',
                        strike.callChangePercent >= 0 ? 'text-emerald-400' : 'text-red-400'
                      )}
                    >
                      {strike.callChangePercent >= 0 ? '+' : ''}{strike.callChangePercent.toFixed(2)}%
                    </span>
                  </td>

                  {/* Strike Price */}
                  <td
                    className={clsx(
                      'text-center font-bold font-mono py-1 px-3 border-x',
                      isATM
                        ? 'bg-yellow-500/15 text-yellow-400 border-yellow-500/30'
                        : isMaxPain
                        ? 'bg-yellow-900/20 text-yellow-600 border-yellow-700/30'
                        : 'bg-bg-secondary text-gray-200 border-bg-border'
                    )}
                  >
                    {strike.strikePrice}
                    {isATM && <div className="text-yellow-500 text-xs font-normal">ATM</div>}
                    {isMaxPain && !isATM && (
                      <div className="text-yellow-700 text-xs font-normal">MaxPain</div>
                    )}
                  </td>

                  {/* PE OI */}
                  <td className={clsx(isITM_PE ? 'bg-emerald-500/5' : '')}>
                    <OICell
                      oi={strike.putOI}
                      maxOI={maxPutOI}
                      type="PE"
                      change={strike.putOIChange}
                    />
                  </td>

                  {/* PE IV */}
                  <td className="text-right px-1.5 py-1">
                    <span className="text-orange-400 font-mono">{strike.putIV.toFixed(1)}%</span>
                  </td>

                  {/* PE LTP */}
                  <td className="text-right px-1.5 py-1">
                    <span className="text-white font-mono">{strike.putLTP.toFixed(2)}</span>
                  </td>

                  {/* PE Change% */}
                  <td className="text-right px-1.5 py-1">
                    <span
                      className={clsx(
                        'font-mono',
                        strike.putChangePercent >= 0 ? 'text-emerald-400' : 'text-red-400'
                      )}
                    >
                      {strike.putChangePercent >= 0 ? '+' : ''}{strike.putChangePercent.toFixed(2)}%
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default OptionChainHeatmap;
