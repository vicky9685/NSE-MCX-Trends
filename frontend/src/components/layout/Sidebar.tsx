import React from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  LayoutDashboard,
  TrendingUp,
  Layers,
  Briefcase,
  Shield,
  Bell,
  Settings,
  Zap,
  Wifi,
  WifiOff,
} from 'lucide-react';
import { clsx } from 'clsx';
import { useTradingStore } from '@/store/tradingStore';
import { ConnectionStatus } from '@/types';

const NAV_ITEMS = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard', exact: true },
  { to: '/signals', icon: TrendingUp, label: 'Signals' },
  { to: '/options', icon: Layers, label: 'Options Chain' },
  { to: '/portfolio', icon: Briefcase, label: 'Portfolio' },
  { to: '/risk', icon: Shield, label: 'Risk' },
  { to: '/alerts', icon: Bell, label: 'Alerts' },
  { to: '/settings', icon: Settings, label: 'Settings' },
];

export const Sidebar: React.FC = () => {
  const location = useLocation();
  const wsStatus = useTradingStore((s) => s.wsStatus);
  const unreadAlerts = useTradingStore((s) => s.unreadAlertsCount);
  const dashboard = useTradingStore((s) => s.dashboard);

  const isConnected = wsStatus === ConnectionStatus.CONNECTED;
  const isConnecting = wsStatus === ConnectionStatus.CONNECTING;

  return (
    <aside className="flex flex-col w-60 shrink-0 bg-bg-secondary border-r border-bg-border h-screen sticky top-0 z-40">
      {/* Logo */}
      <div className="px-5 py-5 border-b border-bg-border">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-accent-blue flex items-center justify-center shrink-0">
            <Zap size={16} className="text-white" />
          </div>
          <div>
            <div className="font-bold text-white text-sm leading-tight">NSE-MCX</div>
            <div className="text-xs text-gray-500 leading-tight">Intelligence</div>
          </div>
        </div>
      </div>

      {/* Market Status */}
      {dashboard && (
        <div className="px-4 py-2 border-b border-bg-border">
          <div className="flex items-center justify-between">
            <span className="text-xs text-gray-500">Market</span>
            <span
              className={clsx(
                'text-xs font-semibold px-1.5 py-0.5 rounded',
                dashboard.marketStatus === 'OPEN'
                  ? 'bg-emerald-500/20 text-emerald-400'
                  : 'bg-gray-600/30 text-gray-400'
              )}
            >
              {dashboard.marketStatus}
            </span>
          </div>
        </div>
      )}

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
        {NAV_ITEMS.map(({ to, icon: Icon, label }) => {
          const isActive = to === '/' ? location.pathname === '/' : location.pathname.startsWith(to);
          const isAlerts = to === '/alerts';

          return (
            <NavLink
              key={to}
              to={to}
              className={clsx(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 relative group',
                isActive
                  ? 'bg-accent-blue text-white shadow-glow'
                  : 'text-gray-400 hover:text-white hover:bg-bg-hover'
              )}
            >
              <Icon size={17} />
              <span>{label}</span>
              {isAlerts && unreadAlerts > 0 && (
                <span className="ml-auto bg-red-500 text-white text-xs px-1.5 py-0.5 rounded-full font-bold min-w-[18px] text-center">
                  {unreadAlerts > 99 ? '99+' : unreadAlerts}
                </span>
              )}
            </NavLink>
          );
        })}
      </nav>

      {/* Footer: WebSocket Status */}
      <div className="px-4 py-4 border-t border-bg-border space-y-2">
        <div className="flex items-center gap-2">
          {isConnected ? (
            <Wifi size={13} className="text-emerald-400" />
          ) : isConnecting ? (
            <Wifi size={13} className="text-yellow-400 animate-pulse" />
          ) : (
            <WifiOff size={13} className="text-red-400" />
          )}
          <span
            className={clsx(
              'text-xs font-medium',
              isConnected ? 'text-emerald-400' : isConnecting ? 'text-yellow-400' : 'text-red-400'
            )}
          >
            {isConnected ? 'Live Feed Active' : isConnecting ? 'Connecting...' : 'Disconnected'}
          </span>
        </div>
        <div className="text-xs text-gray-600">
          NSE-MCX Intelligence v1.0
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;
