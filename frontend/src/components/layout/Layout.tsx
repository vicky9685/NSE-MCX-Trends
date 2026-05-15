import React from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

const PAGE_META: Record<string, { title: string; subtitle?: string }> = {
  '/dashboard': { title: 'Market Dashboard', subtitle: 'Real-time NSE/MCX intelligence' },
  '/signals': { title: 'Trading Signals', subtitle: 'Multi-agent AI generated signals' },
  '/options': { title: 'Options Chain', subtitle: 'Real-time OI, IV & Greeks' },
  '/portfolio': { title: 'Portfolio', subtitle: 'Paper trading positions & P&L tracker' },
  '/risk': { title: 'Risk Management', subtitle: 'VaR, drawdown & position sizing' },
  '/alerts': { title: 'Alerts Center', subtitle: 'Price, technical & news alerts' },
  '/backtesting': { title: 'Backtesting', subtitle: 'Historical strategy performance analysis' },
  '/paper-trading': { title: 'Paper Trading', subtitle: 'Risk-free simulated trading' },
  '/settings': { title: 'Settings', subtitle: 'Broker configuration & preferences' },
};

export const Layout: React.FC = () => {
  const location = useLocation();

  const meta =
    PAGE_META[location.pathname] ||
    PAGE_META[Object.keys(PAGE_META).find((k) => location.pathname.startsWith(k) && k !== '/') || '/'] ||
    { title: 'NSE-MCX Intelligence' };

  return (
    <div className="flex h-screen bg-bg-primary text-gray-200 overflow-hidden">
      <Sidebar />

      <div className="flex flex-col flex-1 min-w-0 overflow-hidden">
        <Header title={meta.title} subtitle={meta.subtitle} />

        <main className="flex-1 overflow-y-auto px-6 py-4">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default Layout;
