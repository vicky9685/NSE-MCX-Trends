import React, { useState } from 'react';
import { TrendingUp, TrendingDown, Target, Shield, Clock, Info, ChevronDown, ChevronUp } from 'lucide-react';
import { clsx } from 'clsx';
import type { TradeSignal } from '@/types';
import { SignalType, TimeHorizon } from '@/types';
import {
  formatCurrency,
  formatPercent,
  getSignalColor,
  getSignalBadge,
  getSignalBgColor,
  getSignalTextClass,
  formatRelativeTime,
} from '@/utils/formatters';

interface SignalCardProps {
  signal: TradeSignal;
  compact?: boolean;
  onClick?: (signal: TradeSignal) => void;
}

const TIME_HORIZON_LABELS: Record<TimeHorizon, string> = {
  [TimeHorizon.INTRADAY]: 'Intraday',
  [TimeHorizon.SWING]: 'Swing',
  [TimeHorizon.POSITIONAL]: 'Positional',
  [TimeHorizon.LONG_TERM]: 'Long Term',
};

const TIME_HORIZON_COLORS: Record<TimeHorizon, string> = {
  [TimeHorizon.INTRADAY]: 'bg-purple-500/20 text-purple-300',
  [TimeHorizon.SWING]: 'bg-blue-500/20 text-blue-300',
  [TimeHorizon.POSITIONAL]: 'bg-cyan-500/20 text-cyan-300',
  [TimeHorizon.LONG_TERM]: 'bg-indigo-500/20 text-indigo-300',
};

export const SignalCard: React.FC<SignalCardProps> = ({ signal, compact = false, onClick }) => {
  const [expanded, setExpanded] = useState(false);
  const [showHedge, setShowHedge] = useState(false);

  const signalColor = getSignalColor(signal.signalType);
  const signalBadge = getSignalBadge(signal.signalType);
  const signalBgClass = getSignalBgColor(signal.signalType);
  const signalTextClass = getSignalTextClass(signal.signalType);
  const isBullish = signal.signalType === SignalType.STRONG_BUY || signal.signalType === SignalType.BUY;

  const upside = signal.target2
    ? ((signal.target2 - signal.entryPrice) / signal.entryPrice) * 100
    : 0;
  const downside = signal.stopLoss
    ? ((signal.entryPrice - signal.stopLoss) / signal.entryPrice) * 100
    : 0;

  return (
    <div
      className={clsx(
        'rounded-xl border bg-bg-card transition-all duration-200 hover:bg-bg-hover cursor-pointer animate-fade-in',
        signalBgClass
      )}
      onClick={() => onClick?.(signal)}
    >
      {/* Header */}
      <div className="p-4">
        <div className="flex items-start justify-between gap-2">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="font-bold text-white text-base font-mono">{signal.symbol}</span>
              <span className="text-xs text-gray-400 font-mono">{signal.exchange}</span>
              <span
                className={clsx(
                  'text-xs px-2 py-0.5 rounded-full font-bold tracking-wide',
                  TIME_HORIZON_COLORS[signal.timeHorizon] || 'bg-gray-500/20 text-gray-300'
                )}
              >
                {TIME_HORIZON_LABELS[signal.timeHorizon] || signal.timeHorizon}
              </span>
            </div>
            <div className="flex items-center gap-1 mt-0.5">
              <span className="text-xs text-gray-500">{signal.agentName}</span>
              <span className="text-gray-600">·</span>
              <span className="text-xs text-gray-500">{formatRelativeTime(signal.generatedAt)}</span>
            </div>
          </div>

          {/* Signal Badge */}
          <div
            className="flex items-center gap-1 px-3 py-1 rounded-lg font-bold text-sm whitespace-nowrap"
            style={{ backgroundColor: `${signalColor}20`, color: signalColor, border: `1px solid ${signalColor}40` }}
          >
            {isBullish ? (
              <TrendingUp size={14} />
            ) : signal.signalType === SignalType.HOLD ? (
              <span>—</span>
            ) : (
              <TrendingDown size={14} />
            )}
            {signalBadge}
          </div>
        </div>

        {/* Confidence Bar */}
        <div className="mt-3">
          <div className="flex justify-between items-center mb-1">
            <span className="text-xs text-gray-400">Confidence</span>
            <span className={clsx('text-xs font-bold', signalTextClass)}>
              {signal.confidenceScore}%
            </span>
          </div>
          <div className="h-1.5 bg-gray-700 rounded-full overflow-hidden">
            <div
              className="h-full rounded-full transition-all duration-500"
              style={{
                width: `${signal.confidenceScore}%`,
                backgroundColor: signalColor,
              }}
            />
          </div>
        </div>

        {/* Price Grid */}
        <div className="mt-3 grid grid-cols-4 gap-2">
          <div className="text-center">
            <div className="text-xs text-gray-500 mb-0.5">Entry</div>
            <div className="text-xs font-bold text-white font-mono">
              {formatCurrency(signal.entryPrice, 'INR')}
            </div>
          </div>
          <div className="text-center">
            <div className="text-xs text-gray-500 mb-0.5">T1</div>
            <div className="text-xs font-bold text-emerald-400 font-mono">
              {formatCurrency(signal.target1, 'INR')}
            </div>
          </div>
          <div className="text-center">
            <div className="text-xs text-gray-500 mb-0.5">T2</div>
            <div className="text-xs font-bold text-emerald-300 font-mono">
              {formatCurrency(signal.target2, 'INR')}
            </div>
          </div>
          <div className="text-center">
            <div className="text-xs text-gray-500 mb-0.5">SL</div>
            <div className="text-xs font-bold text-red-400 font-mono">
              {formatCurrency(signal.stopLoss, 'INR')}
            </div>
          </div>
        </div>

        {/* Risk/Reward Row */}
        <div className="mt-2 flex items-center justify-between text-xs">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1">
              <Target size={11} className="text-gray-400" />
              <span className="text-gray-400">R:R</span>
              <span className="font-bold text-white">{signal.riskRewardRatio.toFixed(2)}</span>
            </div>
            <div className="text-emerald-400">↑ {formatPercent(upside)}</div>
            <div className="text-red-400">↓ {formatPercent(-downside)}</div>
          </div>
          <div className="flex items-center gap-1">
            <Shield size={11} className="text-gray-400" />
            <span className="text-gray-400 text-xs">Risk {signal.maxRiskPercent}%</span>
          </div>
        </div>

        {!compact && (
          <>
            {/* Tags */}
            {signal.tags && signal.tags.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1">
                {signal.tags.slice(0, 4).map((tag) => (
                  <span
                    key={tag}
                    className="text-xs px-1.5 py-0.5 rounded bg-gray-700/50 text-gray-300"
                  >
                    {tag}
                  </span>
                ))}
              </div>
            )}

            {/* Expand/Collapse */}
            <button
              className="mt-2 w-full flex items-center justify-center gap-1 text-xs text-gray-500 hover:text-gray-300 transition-colors py-1"
              onClick={(e) => {
                e.stopPropagation();
                setExpanded((v) => !v);
              }}
            >
              {expanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
              {expanded ? 'Less' : 'More'}
            </button>
          </>
        )}
      </div>

      {/* Expanded Details */}
      {expanded && !compact && (
        <div className="border-t border-white/5 px-4 pb-4 pt-3 space-y-3 animate-fade-in">
          {/* Rationale */}
          {signal.rationale && (
            <div>
              <div className="text-xs font-semibold text-gray-400 mb-1">Rationale</div>
              <p className="text-xs text-gray-300 leading-relaxed">{signal.rationale}</p>
            </div>
          )}

          {/* Scenarios */}
          <div className="grid grid-cols-2 gap-2">
            <div className="rounded-lg bg-emerald-500/10 border border-emerald-500/20 p-2">
              <div className="text-xs font-semibold text-emerald-400 mb-1">Bull Scenario</div>
              <p className="text-xs text-gray-300 leading-relaxed">{signal.scenarioBull}</p>
            </div>
            <div className="rounded-lg bg-red-500/10 border border-red-500/20 p-2">
              <div className="text-xs font-semibold text-red-400 mb-1">Bear Scenario</div>
              <p className="text-xs text-gray-300 leading-relaxed">{signal.scenarioBear}</p>
            </div>
          </div>

          {/* Base Scenario */}
          {signal.scenarioBase && (
            <div className="rounded-lg bg-blue-500/10 border border-blue-500/20 p-2">
              <div className="text-xs font-semibold text-blue-400 mb-1">Base Case</div>
              <p className="text-xs text-gray-300 leading-relaxed">{signal.scenarioBase}</p>
            </div>
          )}

          {/* Technical Summary */}
          {signal.technicalSummary && (
            <div>
              <div className="text-xs font-semibold text-gray-400 mb-1">Technical</div>
              <p className="text-xs text-gray-300 leading-relaxed">{signal.technicalSummary}</p>
            </div>
          )}

          {/* Hedging Strategy */}
          {signal.hedgingStrategy && (
            <div>
              <button
                className="flex items-center gap-1 text-xs font-semibold text-yellow-400 hover:text-yellow-300 transition-colors"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowHedge((v) => !v);
                }}
              >
                <Shield size={12} />
                Hedging Strategy
                <Info size={11} />
              </button>
              {showHedge && (
                <div className="mt-1 rounded-lg bg-yellow-500/10 border border-yellow-500/20 p-2">
                  <p className="text-xs text-gray-300 leading-relaxed">{signal.hedgingStrategy}</p>
                </div>
              )}
            </div>
          )}

          {/* Macro Alignment */}
          {signal.macroAlignment && (
            <div>
              <div className="text-xs font-semibold text-gray-400 mb-1 flex items-center gap-1">
                <Clock size={11} />
                Macro Alignment
              </div>
              <p className="text-xs text-gray-300 leading-relaxed">{signal.macroAlignment}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default SignalCard;
