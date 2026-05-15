import React from 'react';
import { MarketDashboard } from '@/components/dashboard/MarketDashboard';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useMarketData } from '@/hooks/useMarketData';

export const Dashboard: React.FC = () => {
  useWebSocket();
  useMarketData();

  return <MarketDashboard />;
};

export default Dashboard;
