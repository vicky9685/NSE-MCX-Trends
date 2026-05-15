import { SignalType } from '@/types';

// ─── Currency Formatter ───────────────────────────────────────────────────────

export function formatCurrency(
  value: number,
  currency: string = 'INR',
  compact: boolean = false
): string {
  if (compact) {
    if (Math.abs(value) >= 1_00_00_00_000) {
      return `${currency === 'INR' ? '₹' : '$'}${(value / 1_00_00_00_000).toFixed(2)}Cr`;
    }
    if (Math.abs(value) >= 1_00_00_000) {
      return `${currency === 'INR' ? '₹' : '$'}${(value / 1_00_00_000).toFixed(2)}L`;
    }
    if (Math.abs(value) >= 1_000) {
      return `${currency === 'INR' ? '₹' : '$'}${(value / 1_000).toFixed(2)}K`;
    }
  }

  const symbol = currency === 'INR' ? '₹' : currency === 'USD' ? '$' : currency;

  return `${symbol}${new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)}`;
}

// ─── Percent Formatter ────────────────────────────────────────────────────────

export function formatPercent(value: number, decimals: number = 2): string {
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(decimals)}%`;
}

// ─── Number Formatter ─────────────────────────────────────────────────────────

export function formatNumber(value: number, decimals: number = 2): string {
  if (Math.abs(value) >= 1_00_00_00_000) {
    return `${(value / 1_00_00_00_000).toFixed(decimals)}Cr`;
  }
  if (Math.abs(value) >= 1_00_00_000) {
    return `${(value / 1_00_00_000).toFixed(decimals)}L`;
  }
  if (Math.abs(value) >= 1_000) {
    return `${(value / 1_000).toFixed(decimals)}K`;
  }
  return value.toFixed(decimals);
}

export function formatVolume(value: number): string {
  return formatNumber(value, 2);
}

export function formatOI(value: number): string {
  return formatNumber(value, 2);
}

// ─── Signal Colors ────────────────────────────────────────────────────────────

export function getSignalColor(signalType: SignalType): string {
  switch (signalType) {
    case SignalType.STRONG_BUY:
      return '#10b981';
    case SignalType.BUY:
      return '#22c55e';
    case SignalType.HOLD:
      return '#eab308';
    case SignalType.SELL:
      return '#ef4444';
    case SignalType.STRONG_SELL:
      return '#dc2626';
    default:
      return '#6b7280';
  }
}

export function getSignalBgColor(signalType: SignalType): string {
  switch (signalType) {
    case SignalType.STRONG_BUY:
      return 'bg-emerald-500/20 border-emerald-500/40';
    case SignalType.BUY:
      return 'bg-green-500/20 border-green-500/40';
    case SignalType.HOLD:
      return 'bg-yellow-500/20 border-yellow-500/40';
    case SignalType.SELL:
      return 'bg-red-500/20 border-red-500/40';
    case SignalType.STRONG_SELL:
      return 'bg-red-700/20 border-red-700/40';
    default:
      return 'bg-gray-500/20 border-gray-500/40';
  }
}

export function getSignalBadge(signalType: SignalType): string {
  switch (signalType) {
    case SignalType.STRONG_BUY:
      return 'STRONG BUY';
    case SignalType.BUY:
      return 'BUY';
    case SignalType.HOLD:
      return 'HOLD';
    case SignalType.SELL:
      return 'SELL';
    case SignalType.STRONG_SELL:
      return 'STRONG SELL';
    default:
      return 'UNKNOWN';
  }
}

export function getSignalTextClass(signalType: SignalType): string {
  switch (signalType) {
    case SignalType.STRONG_BUY:
      return 'text-emerald-400';
    case SignalType.BUY:
      return 'text-green-400';
    case SignalType.HOLD:
      return 'text-yellow-400';
    case SignalType.SELL:
      return 'text-red-400';
    case SignalType.STRONG_SELL:
      return 'text-red-600';
    default:
      return 'text-gray-400';
  }
}

// ─── Change Color ─────────────────────────────────────────────────────────────

export function getChangeColor(value: number): string {
  if (value > 0) return 'text-emerald-400';
  if (value < 0) return 'text-red-400';
  return 'text-gray-400';
}

export function getChangeBg(value: number): string {
  if (value > 0) return 'bg-emerald-500/10';
  if (value < 0) return 'bg-red-500/10';
  return 'bg-gray-500/10';
}

// ─── Date/Time Formatters ─────────────────────────────────────────────────────

export function formatDateTime(isoString: string): string {
  const date = new Date(isoString);
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
    timeZone: 'Asia/Kolkata',
  }).format(date);
}

export function formatTime(isoString: string): string {
  const date = new Date(isoString);
  return new Intl.DateTimeFormat('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
    timeZone: 'Asia/Kolkata',
  }).format(date);
}

export function formatDate(isoString: string): string {
  const date = new Date(isoString);
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: 'Asia/Kolkata',
  }).format(date);
}

export function formatRelativeTime(isoString: string): string {
  const now = new Date();
  const date = new Date(isoString);
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  return `${diffDays}d ago`;
}

// ─── Sentiment Label ──────────────────────────────────────────────────────────

export function getSentimentLabel(score: number): string {
  if (score <= 30) return 'Bearish';
  if (score <= 50) return 'Cautious';
  if (score <= 70) return 'Neutral';
  if (score <= 85) return 'Bullish';
  return 'Very Bullish';
}

export function getSentimentColor(score: number): string {
  if (score <= 30) return '#ef4444';
  if (score <= 50) return '#f97316';
  if (score <= 70) return '#eab308';
  if (score <= 85) return '#22c55e';
  return '#10b981';
}
