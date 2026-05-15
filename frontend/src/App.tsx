import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import Layout from './components/layout/Layout';
import Dashboard from './pages/Dashboard';
import Signals from './pages/Signals';
import OptionsChain from './pages/OptionsChain';
import Portfolio from './pages/Portfolio';
import RiskManagement from './pages/RiskManagement';
import Alerts from './pages/Alerts';
import Settings from './pages/Settings';
import Backtesting from './pages/Backtesting';
import PaperTrading from './pages/PaperTrading';

export default function App() {
  return (
    <BrowserRouter>
      <Toaster
        position="top-right"
        toastOptions={{
          style: {
            background: '#1e2130',
            color: '#e2e8f0',
            border: '1px solid #2d3748',
            fontFamily: 'JetBrains Mono, Fira Code, monospace',
            fontSize: '13px',
          },
          success: { iconTheme: { primary: '#10b981', secondary: '#fff' } },
          error: { iconTheme: { primary: '#ef4444', secondary: '#fff' } },
        }}
      />
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="signals" element={<Signals />} />
          <Route path="options" element={<OptionsChain />} />
          <Route path="portfolio" element={<Portfolio />} />
          <Route path="risk" element={<RiskManagement />} />
          <Route path="alerts" element={<Alerts />} />
          <Route path="backtesting" element={<Backtesting />} />
          <Route path="paper-trading" element={<PaperTrading />} />
          <Route path="settings" element={<Settings />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
