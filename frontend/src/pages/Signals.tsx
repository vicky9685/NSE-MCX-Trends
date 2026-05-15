import React, { useState, useCallback } from 'react';
import { RefreshCw, Filter, TrendingUp, Search } from 'lucide-react';
import { clsx } from 'clsx';
import { SignalCard } from '@/components/dashboard/SignalCard';
import { useSignals } from '@/hooks/useSignals';
import { useTradingStore } from '@/store/tradingStore';
import { signalsApi } from '@/api/signals';
import { Exchange, Segment, SignalType, TimeHorizon } from '@/types';
import type { TradeSignal } from '@/types';
import toast from 'react-hot-toast';

const SIGNAL_TYPE_OPTIONS = [
  { value: '', label: 'All Signals' },
  { value: SignalType.STRONG_BUY, label: 'Strong Buy' },
  { value: SignalType.BUY, label: 'Buy' },
  { value: SignalType.HOLD, label: 'Hold' },
  { value: SignalType.SELL, label: 'Sell' },
  { value: SignalType.STRONG_SELL, label: 'Strong Sell' },
];

const EXCHANGE_OPTIONS = [
  { value: '', label: 'All Exchanges' },
  { value: Exchange.NSE, label: 'NSE' },
  { value: Exchange.MCX, label: 'MCX' },
  { value: Exchange.NFO, label: 'NFO' },
  { value: Exchange.BSE, label: 'BSE' },
];

const SEGMENT_OPTIONS = [
  { value: '', label: 'All Segments' },
  { value: Segment.EQUITY, label: 'Equity' },
  { value: Segment.FUTURES, label: 'Futures' },
  { value: Segment.OPTIONS, label: 'Options' },
  { value: Segment.COMMODITY, label: 'Commodity' },
];

const HORIZON_OPTIONS = [
  { value: '', label: 'Any Horizon' },
  { value: TimeHorizon.INTRADAY, label: 'Intraday' },
  { value: TimeHorizon.SWING, label: 'Swing' },
  { value: TimeHorizon.POSITIONAL, label: 'Positional' },
  { value: TimeHorizon.LONG_TERM, label: 'Long Term' },
];

export const Signals: React.FC = () => {
  const [signalType, setSignalType] = useState<string>('');
  const [exchange, setExchange] = useState<string>('');
  const [segment, setSegment] = useState<string>('');
  const [horizon, setHorizon] = useState<string>('');
  const [minConfidence, setMinConfidence] = useState<number>(0);
  const [search, setSearch] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [selectedSignal, setSelectedSignal] = useState<TradeSignal | null>(null);

  const { signals, loading, refetch } = useSignals({
    signalType: signalType as SignalType || undefined,
    exchange: exchange as Exchange || undefined,
    segment: segment as Segment || undefined,
    timeHorizon: horizon as TimeHorizon || undefined,
    minConfidence: minConfidence || undefined,
  });

  const filtered = signals.filter((s) =>
    !search || s.symbol.toLowerCase().includes(search.toLowerCase())
  );

  const handleRefreshSignals = async () => {
    setRefreshing(true);
    try {
      await signalsApi.refreshSignals();
      toast.success('Signal refresh triggered. Results will update shortly.', { duration: 4000 });
      setTimeout(refetch, 3000);
    } catch (err) {
      toast.error('Failed to trigger signal refresh');
    } finally {
      setRefreshing(false);
    }
  };

  const selectClass =
    'bg-bg-card border border-bg-border rounded-lg px-3 py-2 text-sm text-white focus:border-accent-blue focus:outline-none transition-colors';

  return (
    <div className="space-y-4">
      {/* Filters Row */}
      <div className="rounded-xl bg-bg-card border border-bg-border p-4">
        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex items-center gap-2">
            <Filter size={14} className="text-gray-400" />
            <span className="text-sm text-gray-400 font-medium">Filters</span>
          </div>

          {/* Search */}
          <div className="relative">
            <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-500" />
            <input
              className="bg-bg-tertiary border border-bg-border rounded-lg pl-8 pr-3 py-2 text-sm text-white focus:border-accent-blue focus:outline-none w-36"
              placeholder="Symbol..."
              value={search}
              onChange={(e) => setSearch(e.target.value.toUpperCase())}
            />
          </div>

          <select className={selectClass} value={signalType} onChange={(e) => setSignalType(e.target.value)}>
            {SIGNAL_TYPE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>

          <select className={selectClass} value={exchange} onChange={(e) => setExchange(e.target.value)}>
            {EXCHANGE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>

          <select className={selectClass} value={segment} onChange={(e) => setSegment(e.target.value)}>
            {SEGMENT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>

          <select className={selectClass} value={horizon} onChange={(e) => setHorizon(e.target.value)}>
            {HORIZON_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>

          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-400">Min Conf:</span>
            <input
              type="range"
              min={0}
              max={100}
              step={5}
              value={minConfidence}
              onChange={(e) => setMinConfidence(parseInt(e.target.value))}
              className="w-24"
            />
            <span className="text-xs text-white font-mono w-8">{minConfidence}%</span>
          </div>

          <button
            onClick={handleRefreshSignals}
            disabled={refreshing}
            className="ml-auto flex items-center gap-2 bg-accent-blue hover:bg-accent-blue-dark text-white px-4 py-2 rounded-lg text-sm font-semibold transition-colors disabled:opacity-50"
          >
            <RefreshCw size={13} className={refreshing ? 'animate-spin' : ''} />
            {refreshing ? 'Refreshing...' : 'Refresh Signals'}
          </button>
        </div>
      </div>

      {/* Stats Row */}
      <div className="grid grid-cols-5 gap-3">
        {Object.values(SignalType).map((st) => {
          const count = signals.filter((s) => s.signalType === st).length;
          const colors: Record<SignalType, string> = {
            [SignalType.STRONG_BUY]: 'text-emerald-400 border-emerald-500/30 bg-emerald-500/5',
            [SignalType.BUY]: 'text-green-400 border-green-500/30 bg-green-500/5',
            [SignalType.HOLD]: 'text-yellow-400 border-yellow-500/30 bg-yellow-500/5',
            [SignalType.SELL]: 'text-red-400 border-red-500/30 bg-red-500/5',
            [SignalType.STRONG_SELL]: 'text-red-600 border-red-700/30 bg-red-700/5',
          };
          return (
            <button
              key={st}
              onClick={() => setSignalType(signalType === st ? '' : st)}
              className={clsx(
                'rounded-xl border p-3 text-center transition-all cursor-pointer',
                colors[st],
                signalType === st ? 'ring-2 ring-accent-blue' : ''
              )}
            >
              <div className="text-2xl font-bold font-mono">{count}</div>
              <div className="text-xs mt-0.5 opacity-80">{st.replace('_', ' ')}</div>
            </button>
          );
        })}
      </div>

      {/* Signal Count */}
      <div className="text-sm text-gray-400">
        Showing <span className="text-white font-semibold">{filtered.length}</span> signals
        {filtered.length !== signals.length && ` (filtered from ${signals.length})`}
      </div>

      {/* Signals Grid */}
      {loading && signals.length === 0 ? (
        <div className="flex items-center justify-center py-16 text-gray-500">
          <RefreshCw size={20} className="animate-spin mr-2" />
          Loading signals...
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <TrendingUp size={32} className="mx-auto mb-2 opacity-30" />
          <div>No signals match your filters</div>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
          {filtered.map((signal) => (
            <SignalCard
              key={signal.id}
              signal={signal}
              onClick={setSelectedSignal}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default Signals;
