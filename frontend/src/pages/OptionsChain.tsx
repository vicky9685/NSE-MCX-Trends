import React, { useState, useEffect, useCallback } from 'react';
import { RefreshCw, TrendingUp, TrendingDown } from 'lucide-react';
import { clsx } from 'clsx';
import { OptionChainHeatmap } from '@/components/charts/OptionChainHeatmap';
import { useTradingStore } from '@/store/tradingStore';
import { optionsApi } from '@/api/options';
import { formatCurrency, formatPercent } from '@/utils/formatters';

const UNDERLYING_SYMBOLS = ['NIFTY', 'BANKNIFTY', 'FINNIFTY', 'MIDCPNIFTY', 'SENSEX'];

export const OptionsChain: React.FC = () => {
  const {
    optionChain,
    optionChainLoading,
    optionChainSymbol,
    optionChainExpiry,
    setOptionChain,
    setOptionChainLoading,
    setOptionChainSymbol,
    setOptionChainExpiry,
  } = useTradingStore();

  const [expiries, setExpiries] = useState<string[]>([]);
  const [selectedStrike, setSelectedStrike] = useState<number | null>(null);

  const fetchExpiries = useCallback(async (symbol: string) => {
    try {
      const data = await optionsApi.getExpiries(symbol);
      setExpiries(data);
      if (data.length > 0 && !optionChainExpiry) {
        setOptionChainExpiry(data[0]);
      }
    } catch (err) {
      console.error('[OptionsChain] fetchExpiries error:', err);
    }
  }, [optionChainExpiry, setOptionChainExpiry]);

  const fetchChain = useCallback(async () => {
    if (!optionChainSymbol) return;
    setOptionChainLoading(true);
    try {
      const data = await optionsApi.getOptionChain(optionChainSymbol, optionChainExpiry || undefined);
      setOptionChain(data);
    } catch (err) {
      console.error('[OptionsChain] fetchChain error:', err);
    } finally {
      setOptionChainLoading(false);
    }
  }, [optionChainSymbol, optionChainExpiry, setOptionChain, setOptionChainLoading]);

  useEffect(() => {
    fetchExpiries(optionChainSymbol);
  }, [optionChainSymbol, fetchExpiries]);

  useEffect(() => {
    fetchChain();
    const t = setInterval(fetchChain, 15000);
    return () => clearInterval(t);
  }, [fetchChain]);

  const selectedStrikeData = selectedStrike && optionChain
    ? optionChain.strikes.find((s) => s.strikePrice === selectedStrike)
    : null;

  return (
    <div className="space-y-4">
      {/* Controls */}
      <div className="flex items-center gap-3 flex-wrap">
        {/* Symbol Selector */}
        <div className="flex items-center gap-1 rounded-lg bg-bg-card border border-bg-border p-1">
          {UNDERLYING_SYMBOLS.map((sym) => (
            <button
              key={sym}
              onClick={() => {
                setOptionChainSymbol(sym);
                setOptionChainExpiry('');
                setSelectedStrike(null);
              }}
              className={clsx(
                'px-3 py-1.5 rounded-md text-sm font-semibold transition-colors',
                optionChainSymbol === sym
                  ? 'bg-accent-blue text-white'
                  : 'text-gray-400 hover:text-white'
              )}
            >
              {sym}
            </button>
          ))}
        </div>

        {/* Expiry Selector */}
        {expiries.length > 0 && (
          <div className="flex items-center gap-1 rounded-lg bg-bg-card border border-bg-border p-1 flex-wrap">
            {expiries.slice(0, 6).map((exp) => (
              <button
                key={exp}
                onClick={() => setOptionChainExpiry(exp)}
                className={clsx(
                  'px-2.5 py-1 rounded-md text-xs font-semibold transition-colors',
                  optionChainExpiry === exp
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-400 hover:text-white'
                )}
              >
                {exp}
              </button>
            ))}
          </div>
        )}

        <button
          onClick={fetchChain}
          disabled={optionChainLoading}
          className="ml-auto flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors border border-bg-border rounded-lg px-3 py-2 bg-bg-card"
        >
          <RefreshCw size={13} className={optionChainLoading ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      {/* Selected Strike Details */}
      {selectedStrikeData && (
        <div className="rounded-xl bg-bg-card border border-yellow-500/30 p-4 animate-fade-in">
          <div className="text-sm font-semibold text-yellow-400 mb-3">
            Strike {selectedStrikeData.strikePrice} — Greeks & Analysis
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {/* CE Greeks */}
            <div>
              <div className="text-xs text-red-400 font-semibold mb-2">CALL (CE)</div>
              <div className="space-y-1 text-xs font-mono">
                <div className="flex justify-between"><span className="text-gray-400">Delta</span><span className="text-white">{selectedStrikeData.callDelta.toFixed(4)}</span></div>
                <div className="flex justify-between"><span className="text-gray-400">Gamma</span><span className="text-white">{selectedStrikeData.callGamma.toFixed(4)}</span></div>
                <div className="flex justify-between"><span className="text-gray-400">Theta</span><span className="text-red-400">{selectedStrikeData.callTheta.toFixed(4)}</span></div>
                <div className="flex justify-between"><span className="text-gray-400">Vega</span><span className="text-blue-400">{selectedStrikeData.callVega.toFixed(4)}</span></div>
              </div>
            </div>
            {/* CE Data */}
            <div>
              <div className="text-xs text-red-400 font-semibold mb-2">CE Market</div>
              <div className="space-y-1 text-xs font-mono">
                <div className="flex justify-between"><span className="text-gray-400">LTP</span><span className="text-white">{selectedStrikeData.callLTP.toFixed(2)}</span></div>
                <div className="flex justify-between"><span className="text-gray-400">IV</span><span className="text-orange-400">{selectedStrikeData.callIV.toFixed(2)}%</span></div>
                <div className="flex justify-between"><span className="text-gray-400">OI</span><span className="text-white">{(selectedStrikeData.callOI / 100000).toFixed(2)}L</span></div>
                <div className="flex justify-between"><span className="text-gray-400">PCR</span><span className="text-yellow-400">{selectedStrikeData.pcr.toFixed(2)}</span></div>
              </div>
            </div>
            {/* PE Data */}
            <div>
              <div className="text-xs text-emerald-400 font-semibold mb-2">PE Market</div>
              <div className="space-y-1 text-xs font-mono">
                <div className="flex justify-between"><span className="text-gray-400">LTP</span><span className="text-white">{selectedStrikeData.putLTP.toFixed(2)}</span></div>
                <div className="flex justify-between"><span className="text-gray-400">IV</span><span className="text-orange-400">{selectedStrikeData.putIV.toFixed(2)}%</span></div>
                <div className="flex justify-between"><span className="text-gray-400">OI</span><span className="text-white">{(selectedStrikeData.putOI / 100000).toFixed(2)}L</span></div>
                <div className="flex justify-between"><span className="text-gray-400">Vol</span><span className="text-white">{(selectedStrikeData.putVolume / 1000).toFixed(1)}K</span></div>
              </div>
            </div>
            {/* PE Greeks */}
            <div>
              <div className="text-xs text-emerald-400 font-semibold mb-2">PUT (PE)</div>
              <div className="space-y-1 text-xs font-mono">
                <div className="flex justify-between"><span className="text-gray-400">Delta</span><span className="text-white">{selectedStrikeData.putDelta.toFixed(4)}</span></div>
                <div className="flex justify-between"><span className="text-gray-400">Gamma</span><span className="text-white">{selectedStrikeData.putGamma.toFixed(4)}</span></div>
                <div className="flex justify-between"><span className="text-gray-400">Theta</span><span className="text-red-400">{selectedStrikeData.putTheta.toFixed(4)}</span></div>
                <div className="flex justify-between"><span className="text-gray-400">Vega</span><span className="text-blue-400">{selectedStrikeData.putVega.toFixed(4)}</span></div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Option Chain Table */}
      {optionChainLoading && !optionChain ? (
        <div className="flex items-center justify-center py-16 text-gray-500">
          <RefreshCw size={20} className="animate-spin mr-2" />
          Loading option chain...
        </div>
      ) : (
        <OptionChainHeatmap
          chain={optionChain}
          onStrikeSelect={setSelectedStrike}
          selectedStrike={selectedStrike}
        />
      )}
    </div>
  );
};

export default OptionsChain;
