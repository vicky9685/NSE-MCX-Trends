import React from 'react';
import { Activity, Bell, RefreshCw, Clock } from 'lucide-react';
import { clsx } from 'clsx';
import { useTradingStore } from '@/store/tradingStore';
import { formatRelativeTime } from '@/utils/formatters';
import { useNavigate } from 'react-router-dom';

interface HeaderProps {
  title: string;
  subtitle?: string;
}

function LiveClock() {
  const [time, setTime] = React.useState(new Date());

  React.useEffect(() => {
    const t = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(t);
  }, []);

  const timeStr = time.toLocaleTimeString('en-IN', {
    timeZone: 'Asia/Kolkata',
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });

  const dateStr = time.toLocaleDateString('en-IN', {
    timeZone: 'Asia/Kolkata',
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });

  return (
    <div className="flex items-center gap-2">
      <Clock size={13} className="text-gray-500" />
      <div className="text-xs">
        <span className="font-mono text-white font-semibold">{timeStr}</span>
        <span className="text-gray-500 ml-1">IST</span>
        <span className="text-gray-600 ml-1">·</span>
        <span className="text-gray-500 ml-1">{dateStr}</span>
      </div>
    </div>
  );
}

export const Header: React.FC<HeaderProps> = ({ title, subtitle }) => {
  const navigate = useNavigate();
  const indiaVix = useTradingStore((s) => s.indiaVix);
  const indiaVixChange = useTradingStore((s) => s.indiaVixChange);
  const unreadAlerts = useTradingStore((s) => s.unreadAlertsCount);
  const dashboard = useTradingStore((s) => s.dashboard);

  const vixColor =
    indiaVix > 25
      ? 'text-red-400'
      : indiaVix > 20
      ? 'text-orange-400'
      : indiaVix > 15
      ? 'text-yellow-400'
      : 'text-emerald-400';

  return (
    <header className="sticky top-0 z-30 bg-bg-primary/90 backdrop-blur-sm border-b border-bg-border px-6 py-3 flex items-center gap-4">
      {/* Title */}
      <div className="flex-1 min-w-0">
        <h1 className="font-bold text-white text-lg leading-tight">{title}</h1>
        {subtitle && <p className="text-xs text-gray-500 mt-0.5">{subtitle}</p>}
      </div>

      {/* India VIX */}
      {indiaVix > 0 && (
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-bg-card border border-bg-border">
          <Activity size={13} className={vixColor} />
          <span className="text-xs text-gray-400">India VIX</span>
          <span className={clsx('text-sm font-bold font-mono', vixColor)}>
            {indiaVix.toFixed(2)}
          </span>
          <span
            className={clsx(
              'text-xs font-mono',
              indiaVixChange >= 0 ? 'text-red-400' : 'text-emerald-400'
            )}
          >
            {indiaVixChange >= 0 ? '+' : ''}{indiaVixChange.toFixed(2)}%
          </span>
        </div>
      )}

      {/* Live Clock */}
      <LiveClock />

      {/* Last Updated */}
      {dashboard?.timestamp && (
        <div className="hidden xl:flex items-center gap-1 text-xs text-gray-600">
          <RefreshCw size={11} />
          {formatRelativeTime(dashboard.timestamp)}
        </div>
      )}

      {/* Alert Bell */}
      <button
        onClick={() => navigate('/alerts')}
        className="relative p-2 rounded-lg hover:bg-bg-hover transition-colors"
        aria-label="Alerts"
      >
        <Bell size={17} className={unreadAlerts > 0 ? 'text-accent-blue' : 'text-gray-500'} />
        {unreadAlerts > 0 && (
          <span className="absolute -top-0.5 -right-0.5 bg-red-500 text-white text-xs rounded-full w-4 h-4 flex items-center justify-center font-bold text-[10px]">
            {unreadAlerts > 9 ? '9+' : unreadAlerts}
          </span>
        )}
      </button>
    </header>
  );
};

export default Header;
