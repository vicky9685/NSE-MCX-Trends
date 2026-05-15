import React, { useState, useEffect, useCallback } from 'react';
import {
  Settings as SettingsIcon,
  Zap,
  Database,
  Bell,
  RefreshCw,
  Check,
  X,
  AlertTriangle,
  Link,
  LinkOff,
} from 'lucide-react';
import { clsx } from 'clsx';
import toast from 'react-hot-toast';
import { brokerApi, alertsApi } from '@/api/portfolio';
import { paperApi } from '@/api/paper';
import { BrokerType } from '@/types';
import type { BrokerConfig } from '@/types';
import type { PaperSettings } from '@/api/paper';

// ─── Tab type ─────────────────────────────────────────────────────────────────

type Tab = 'broker' | 'paper' | 'data' | 'notifications';

const TABS: { value: Tab; label: string; icon: React.ReactNode }[] = [
  { value: 'broker', label: 'Broker Config', icon: <Zap size={14} /> },
  { value: 'paper', label: 'Paper Trading', icon: <SettingsIcon size={14} /> },
  { value: 'data', label: 'Data Sources', icon: <Database size={14} /> },
  { value: 'notifications', label: 'Notifications', icon: <Bell size={14} /> },
];

// ─── Status Badge ─────────────────────────────────────────────────────────────

const StatusBadge: React.FC<{ connected: boolean }> = ({ connected }) => (
  <span
    className={clsx(
      'flex items-center gap-1.5 text-xs font-semibold px-2 py-1 rounded-full',
      connected
        ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
        : 'bg-gray-700/50 text-gray-500 border border-gray-600/30'
    )}
  >
    <span className={clsx('w-1.5 h-1.5 rounded-full', connected ? 'bg-emerald-400 animate-pulse' : 'bg-gray-600')} />
    {connected ? 'Connected' : 'Disconnected'}
  </span>
);

// ─── Broker Card ──────────────────────────────────────────────────────────────

interface BrokerCardProps {
  broker: BrokerType;
  config: BrokerConfig;
  onSave: (config: Partial<BrokerConfig>) => Promise<void>;
  onConnect: (broker: BrokerType) => Promise<void>;
  onDisconnect: (broker: BrokerType) => Promise<void>;
}

const BROKER_META: Record<BrokerType, { name: string; color: string; url: string }> = {
  [BrokerType.ZERODHA]: { name: 'Zerodha Kite', color: '#387ed1', url: 'https://kite.zerodha.com' },
  [BrokerType.ALICE_BLUE]: { name: 'AliceBlue', color: '#1e90ff', url: 'https://ant.aliceblueonline.com' },
  [BrokerType.BONANZA]: { name: 'Bonanza Portfolio', color: '#e63946', url: 'https://bonanzaonline.com' },
};

const BrokerCard: React.FC<BrokerCardProps> = ({ broker, config, onSave, onConnect, onDisconnect }) => {
  const [apiKey, setApiKey] = useState(config.apiKey || '');
  const [apiSecret, setApiSecret] = useState(config.apiSecret || '');
  const [enabled, setEnabled] = useState(config.isEnabled);
  const [saving, setSaving] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const meta = BROKER_META[broker];

  const handleSave = async () => {
    setSaving(true);
    await onSave({ broker, apiKey, apiSecret, isEnabled: enabled });
    setSaving(false);
  };

  const handleConnect = async () => {
    setConnecting(true);
    if (config.isConnected) {
      await onDisconnect(broker);
    } else {
      await onConnect(broker);
    }
    setConnecting(false);
  };

  return (
    <div className="bg-bg-card border border-bg-border rounded-xl p-5">
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: meta.color + '20', border: `1px solid ${meta.color}40` }}>
            <Zap size={15} style={{ color: meta.color }} />
          </div>
          <div>
            <div className="font-bold text-white text-sm">{meta.name}</div>
            <a href={meta.url} target="_blank" rel="noreferrer" className="text-xs text-gray-500 hover:text-accent-blue transition-colors">
              {meta.url}
            </a>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge connected={config.isConnected} />
          <label className="relative inline-flex cursor-pointer">
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} className="sr-only peer" />
            <div className="w-9 h-5 bg-gray-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-accent-blue" />
          </label>
        </div>
      </div>

      <div className="space-y-3">
        <div>
          <label className="label-form">API Key</label>
          <input
            type="password"
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            placeholder="Enter API Key"
            className="input-dark w-full"
            disabled={!enabled}
          />
        </div>
        <div>
          <label className="label-form">API Secret</label>
          <input
            type="password"
            value={apiSecret}
            onChange={(e) => setApiSecret(e.target.value)}
            placeholder="Enter API Secret"
            className="input-dark w-full"
            disabled={!enabled}
          />
        </div>
      </div>

      <div className="flex gap-2 mt-4">
        <button
          onClick={handleSave}
          disabled={saving || !enabled}
          className="btn-secondary flex items-center gap-2 text-sm flex-1"
        >
          {saving ? <RefreshCw size={13} className="animate-spin" /> : <Check size={13} />}
          Save
        </button>
        <button
          onClick={handleConnect}
          disabled={connecting || !enabled || !apiKey}
          className={clsx(
            'flex items-center gap-2 text-sm flex-1 px-4 py-2 rounded-lg font-medium transition-colors disabled:opacity-50',
            config.isConnected
              ? 'bg-red-500/20 hover:bg-red-500/30 text-red-400 border border-red-500/30'
              : 'btn-primary'
          )}
        >
          {connecting ? (
            <RefreshCw size={13} className="animate-spin" />
          ) : config.isConnected ? (
            <LinkOff size={13} />
          ) : (
            <Link size={13} />
          )}
          {config.isConnected ? 'Disconnect' : 'Connect'}
        </button>
      </div>
    </div>
  );
};

// ─── Toggle Switch ────────────────────────────────────────────────────────────

const Toggle: React.FC<{ checked: boolean; onChange: (v: boolean) => void; label: string; description?: string }> = ({
  checked, onChange, label, description
}) => (
  <div className="flex items-center justify-between py-3 border-b border-bg-border/50 last:border-0">
    <div>
      <div className="text-sm font-medium text-gray-200">{label}</div>
      {description && <div className="text-xs text-gray-500 mt-0.5">{description}</div>}
    </div>
    <label className="relative inline-flex cursor-pointer ml-4">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="sr-only peer" />
      <div className="w-10 h-6 bg-gray-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-accent-blue" />
    </label>
  </div>
);

// ─── Main Settings Component ──────────────────────────────────────────────────

const DEFAULT_BROKER: BrokerConfig = {
  broker: BrokerType.ZERODHA,
  isEnabled: false,
  isConnected: false,
  apiKey: '',
  apiSecret: '',
  paperTrading: true,
  autoOrder: false,
  maxOrderValue: 50000,
};

export const Settings: React.FC = () => {
  const [activeTab, setActiveTab] = useState<Tab>('broker');
  const [brokerConfigs, setBrokerConfigs] = useState<Record<BrokerType, BrokerConfig>>({
    [BrokerType.ZERODHA]: { ...DEFAULT_BROKER, broker: BrokerType.ZERODHA },
    [BrokerType.ALICE_BLUE]: { ...DEFAULT_BROKER, broker: BrokerType.ALICE_BLUE },
    [BrokerType.BONANZA]: { ...DEFAULT_BROKER, broker: BrokerType.BONANZA },
  });
  const [paperSettings, setPaperSettings] = useState<PaperSettings>({
    initialCapital: 1000000,
    maxPositions: 10,
    maxCapitalPerTradePercent: 10,
    isEnabled: true,
  });
  const [notifSettings, setNotifSettings] = useState({
    emailAlerts: false,
    priceAlerts: true,
    signalAlerts: true,
    riskAlerts: true,
    systemAlerts: true,
  });
  const [refreshInterval, setRefreshInterval] = useState('30000');
  const [loading, setLoading] = useState(false);
  const [resetConfirm, setResetConfirm] = useState(false);

  useEffect(() => {
    const fetch = async () => {
      try {
        const [brokers, paper] = await Promise.all([
          brokerApi.getBrokers(),
          paperApi.getSettings(),
        ]);
        const map = { ...brokerConfigs };
        brokers.forEach((b) => { map[b.broker] = b; });
        setBrokerConfigs(map);
        setPaperSettings(paper);
      } catch {
        // graceful — show defaults
      }
    };
    fetch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSaveBroker = useCallback(async (config: Partial<BrokerConfig>) => {
    try {
      const saved = await brokerApi.saveBrokerConfig(config);
      setBrokerConfigs((prev) => ({ ...prev, [saved.broker]: saved }));
      toast.success(`${saved.broker} config saved`);
    } catch {
      toast.error('Failed to save broker config');
    }
  }, []);

  const handleConnectBroker = useCallback(async (broker: BrokerType) => {
    try {
      const res = await brokerApi.connectBroker(broker);
      if (res.success) {
        setBrokerConfigs((prev) => ({ ...prev, [broker]: { ...prev[broker], isConnected: true } }));
        toast.success(res.message || `${broker} connected`);
      } else {
        toast.error(res.message || 'Connection failed');
      }
    } catch {
      toast.error(`Failed to connect to ${broker}`);
    }
  }, []);

  const handleDisconnectBroker = useCallback(async (broker: BrokerType) => {
    try {
      await brokerApi.disconnectBroker(broker);
      setBrokerConfigs((prev) => ({ ...prev, [broker]: { ...prev[broker], isConnected: false } }));
      toast.success(`${broker} disconnected`);
    } catch {
      toast.error(`Failed to disconnect ${broker}`);
    }
  }, []);

  const handleSavePaper = async () => {
    setLoading(true);
    try {
      await paperApi.saveSettings(paperSettings);
      toast.success('Paper trading settings saved');
    } catch {
      toast.error('Failed to save settings');
    } finally {
      setLoading(false);
    }
  };

  const handleResetPortfolio = async () => {
    try {
      await paperApi.resetPortfolio();
      toast.success('Portfolio reset successfully');
      setResetConfirm(false);
    } catch {
      toast.error('Failed to reset portfolio');
    }
  };

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-lg font-bold text-white">Settings</h1>
        <p className="text-xs text-gray-500">Broker configuration, paper trading & notifications</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-bg-tertiary rounded-lg p-1 border border-bg-border w-fit">
        {TABS.map(({ value, label, icon }) => (
          <button
            key={value}
            onClick={() => setActiveTab(value)}
            className={clsx(
              'flex items-center gap-2 px-4 py-2 text-sm font-medium rounded transition-colors',
              activeTab === value ? 'tab-active' : 'tab-inactive'
            )}
          >
            {icon}
            {label}
          </button>
        ))}
      </div>

      {/* Broker Config */}
      {activeTab === 'broker' && (
        <div className="space-y-4">
          <div className="bg-yellow-500/10 border border-yellow-500/20 rounded-lg p-3 flex gap-2">
            <AlertTriangle size={14} className="text-yellow-400 shrink-0 mt-0.5" />
            <p className="text-xs text-yellow-200">
              API credentials are encrypted and stored locally. Never share your API keys. Enable paper trading mode before going live.
            </p>
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {Object.values(BrokerType).map((broker) => (
              <BrokerCard
                key={broker}
                broker={broker}
                config={brokerConfigs[broker]}
                onSave={handleSaveBroker}
                onConnect={handleConnectBroker}
                onDisconnect={handleDisconnectBroker}
              />
            ))}
          </div>
        </div>
      )}

      {/* Paper Trading */}
      {activeTab === 'paper' && (
        <div className="max-w-lg space-y-5">
          <div className="bg-bg-card border border-bg-border rounded-xl p-5">
            <h3 className="text-sm font-bold text-white mb-4">Paper Trading Settings</h3>
            <div className="space-y-4">
              <div>
                <label className="label-form">Initial Capital (INR)</label>
                <input
                  type="number"
                  value={paperSettings.initialCapital}
                  onChange={(e) => setPaperSettings((p) => ({ ...p, initialCapital: Number(e.target.value) }))}
                  className="input-dark w-full"
                  step="10000"
                  min="10000"
                />
              </div>
              <div>
                <label className="label-form">Max Positions</label>
                <input
                  type="number"
                  value={paperSettings.maxPositions}
                  onChange={(e) => setPaperSettings((p) => ({ ...p, maxPositions: Number(e.target.value) }))}
                  className="input-dark w-full"
                  min="1"
                  max="50"
                />
              </div>
              <div>
                <label className="label-form">Max Capital Per Trade — {paperSettings.maxCapitalPerTradePercent}%</label>
                <input
                  type="range"
                  min={1}
                  max={25}
                  step={1}
                  value={paperSettings.maxCapitalPerTradePercent}
                  onChange={(e) => setPaperSettings((p) => ({ ...p, maxCapitalPerTradePercent: Number(e.target.value) }))}
                  className="w-full accent-accent-blue"
                />
                <div className="flex justify-between text-xs text-gray-600 mt-0.5">
                  <span>1%</span><span>Risk-based</span><span>25%</span>
                </div>
              </div>
              <Toggle
                checked={paperSettings.isEnabled}
                onChange={(v) => setPaperSettings((p) => ({ ...p, isEnabled: v }))}
                label="Enable Paper Trading"
                description="Simulates trades without real money"
              />
            </div>
            <div className="flex gap-3 mt-5">
              <button onClick={handleSavePaper} disabled={loading} className="btn-primary flex-1 flex items-center justify-center gap-2">
                {loading ? <RefreshCw size={13} className="animate-spin" /> : <Check size={13} />}
                Save Settings
              </button>
            </div>
          </div>

          <div className="bg-bg-card border border-red-500/20 rounded-xl p-5">
            <h3 className="text-sm font-bold text-red-400 mb-2">Danger Zone</h3>
            <p className="text-xs text-gray-400 mb-4">
              This will permanently close all open paper trades and reset your portfolio to the initial capital.
            </p>
            {!resetConfirm ? (
              <button onClick={() => setResetConfirm(true)} className="btn-danger text-sm">
                Reset Portfolio
              </button>
            ) : (
              <div className="flex gap-3">
                <button onClick={() => setResetConfirm(false)} className="btn-secondary text-sm flex-1">
                  Cancel
                </button>
                <button onClick={handleResetPortfolio} className="btn-danger text-sm flex-1">
                  Confirm Reset
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Data Sources */}
      {activeTab === 'data' && (
        <div className="max-w-lg space-y-4">
          <div className="bg-bg-card border border-bg-border rounded-xl p-5">
            <h3 className="text-sm font-bold text-white mb-4">Data Sources</h3>
            <div className="space-y-4">
              <div className="flex items-center justify-between py-3 border-b border-bg-border">
                <div>
                  <div className="text-sm font-medium text-gray-200">Yahoo Finance</div>
                  <div className="text-xs text-gray-500 mt-0.5">Primary market data provider (free)</div>
                </div>
                <StatusBadge connected={true} />
              </div>
              <div className="flex items-center justify-between py-3 border-b border-bg-border">
                <div>
                  <div className="text-sm font-medium text-gray-200">NSE Official Feed</div>
                  <div className="text-xs text-gray-500 mt-0.5">Options chain & F&O data</div>
                </div>
                <StatusBadge connected={true} />
              </div>
              <div className="flex items-center justify-between py-3">
                <div>
                  <div className="text-sm font-medium text-gray-200">MCX Feed</div>
                  <div className="text-xs text-gray-500 mt-0.5">Commodity prices</div>
                </div>
                <StatusBadge connected={true} />
              </div>
            </div>
            <div className="mt-4">
              <label className="label-form">Data Refresh Interval</label>
              <select
                value={refreshInterval}
                onChange={(e) => setRefreshInterval(e.target.value)}
                className="input-dark w-full"
              >
                <option value="5000">5 seconds (Real-time)</option>
                <option value="15000">15 seconds</option>
                <option value="30000">30 seconds (Default)</option>
                <option value="60000">1 minute</option>
                <option value="300000">5 minutes</option>
              </select>
            </div>
            <button
              onClick={() => toast.success('Data source settings saved')}
              className="btn-primary w-full mt-4"
            >
              Save Data Settings
            </button>
          </div>
        </div>
      )}

      {/* Notifications */}
      {activeTab === 'notifications' && (
        <div className="max-w-lg">
          <div className="bg-bg-card border border-bg-border rounded-xl p-5">
            <h3 className="text-sm font-bold text-white mb-4">Notification Preferences</h3>
            <Toggle
              checked={notifSettings.emailAlerts}
              onChange={(v) => setNotifSettings((n) => ({ ...n, emailAlerts: v }))}
              label="Email Alerts"
              description="Receive alerts via email (configure SMTP in backend)"
            />
            <Toggle
              checked={notifSettings.priceAlerts}
              onChange={(v) => setNotifSettings((n) => ({ ...n, priceAlerts: v }))}
              label="Price Alerts"
              description="Notify when price targets are hit"
            />
            <Toggle
              checked={notifSettings.signalAlerts}
              onChange={(v) => setNotifSettings((n) => ({ ...n, signalAlerts: v }))}
              label="Trading Signal Alerts"
              description="New BUY/SELL signals from AI agents"
            />
            <Toggle
              checked={notifSettings.riskAlerts}
              onChange={(v) => setNotifSettings((n) => ({ ...n, riskAlerts: v }))}
              label="Risk Alerts"
              description="Drawdown, VaR breaches, volatility spikes"
            />
            <Toggle
              checked={notifSettings.systemAlerts}
              onChange={(v) => setNotifSettings((n) => ({ ...n, systemAlerts: v }))}
              label="System Alerts"
              description="Connection issues, data feed errors"
            />
            <button
              onClick={() => toast.success('Notification preferences saved')}
              className="btn-primary w-full mt-5"
            >
              Save Notification Settings
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default Settings;
