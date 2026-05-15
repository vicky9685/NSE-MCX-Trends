import React, { useState, useEffect, useCallback } from 'react';
import { Bell, BellOff, Plus, X, Check, CheckCheck, AlertTriangle, Info, Zap, Filter } from 'lucide-react';
import { clsx } from 'clsx';
import toast from 'react-hot-toast';
import { alertsApi } from '@/api/portfolio';
import { useTradingStore } from '@/store/tradingStore';
import { AlertType, AlertSeverity, Exchange } from '@/types';
import type { Alert, CreateAlertRequest } from '@/types';
import { formatRelativeTime } from '@/utils/formatters';

// ─── Constants ────────────────────────────────────────────────────────────────

type FilterTab = 'ALL' | 'TRIGGERED' | AlertType;

const FILTER_TABS: { value: FilterTab; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'TRIGGERED', label: 'Triggered' },
  { value: AlertType.PRICE, label: 'Price' },
  { value: AlertType.TECHNICAL, label: 'Technical' },
  { value: AlertType.SIGNAL, label: 'Signal' },
  { value: AlertType.RISK, label: 'Risk' },
];

const SEVERITY_CONFIG = {
  [AlertSeverity.CRITICAL]: {
    badge: 'bg-red-500/20 text-red-400 border-red-500/30',
    icon: <AlertTriangle size={14} className="text-red-400" />,
    border: 'border-l-red-500',
    dot: 'bg-red-500',
  },
  [AlertSeverity.WARNING]: {
    badge: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
    icon: <AlertTriangle size={14} className="text-yellow-400" />,
    border: 'border-l-yellow-500',
    dot: 'bg-yellow-500',
  },
  [AlertSeverity.INFO]: {
    badge: 'bg-blue-500/20 text-blue-400 border-blue-500/30',
    icon: <Info size={14} className="text-blue-400" />,
    border: 'border-l-blue-500',
    dot: 'bg-blue-400',
  },
};

// ─── Create Alert Modal ───────────────────────────────────────────────────────

interface CreateModalProps {
  onClose: () => void;
  onCreate: (req: CreateAlertRequest) => void;
}

const INSTRUMENTS = [
  'NIFTY50', 'BANKNIFTY', 'RELIANCE', 'TCS', 'HDFCBANK', 'INFY', 'ICICIBANK',
  'SBIN', 'AXISBANK', 'KOTAKBANK', 'BHARTIARTL', 'WIPRO', 'HCLTECH', 'ITC',
  'LT', 'SUNPHARMA', 'TATAMOTORS', 'MARUTI', 'GOLD', 'SILVER', 'CRUDEOIL',
];

const CreateAlertModal: React.FC<CreateModalProps> = ({ onClose, onCreate }) => {
  const [form, setForm] = useState<CreateAlertRequest>({
    type: AlertType.PRICE,
    severity: AlertSeverity.INFO,
    symbol: 'NIFTY50',
    exchange: Exchange.NSE,
    title: '',
    message: '',
    condition: 'ABOVE',
    targetValue: undefined,
    notifyEmail: false,
    notifyPush: true,
    notifySms: false,
  });
  const [symbolSearch, setSymbolSearch] = useState('');
  const [showDropdown, setShowDropdown] = useState(false);

  const filteredSymbols = INSTRUMENTS.filter((s) =>
    s.toLowerCase().includes(symbolSearch.toLowerCase())
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title.trim()) { toast.error('Alert title is required'); return; }
    if (!form.targetValue) { toast.error('Target value is required'); return; }
    onCreate(form);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="bg-bg-secondary border border-bg-border rounded-xl p-6 w-[480px] shadow-xl max-h-[90vh] overflow-y-auto">
        <div className="flex justify-between items-center mb-5">
          <h3 className="font-bold text-white flex items-center gap-2">
            <Bell size={16} className="text-accent-blue" />
            Create New Alert
          </h3>
          <button onClick={onClose} className="text-gray-500 hover:text-white">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Instrument */}
          <div className="relative">
            <label className="label-form">Instrument</label>
            <input
              type="text"
              value={form.symbol || symbolSearch}
              onChange={(e) => {
                setSymbolSearch(e.target.value);
                setShowDropdown(true);
                setForm((f) => ({ ...f, symbol: e.target.value }));
              }}
              onFocus={() => setShowDropdown(true)}
              placeholder="Search symbol..."
              className="input-dark w-full"
            />
            {showDropdown && filteredSymbols.length > 0 && (
              <div className="absolute z-10 top-full left-0 right-0 bg-bg-tertiary border border-bg-border rounded-lg shadow-xl max-h-40 overflow-y-auto mt-1">
                {filteredSymbols.map((sym) => (
                  <button
                    key={sym}
                    type="button"
                    onClick={() => {
                      setForm((f) => ({ ...f, symbol: sym }));
                      setSymbolSearch(sym);
                      setShowDropdown(false);
                    }}
                    className="w-full text-left px-3 py-2 text-sm hover:bg-bg-hover text-gray-300"
                  >
                    {sym}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Type + Severity */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label-form">Alert Type</label>
              <select
                value={form.type}
                onChange={(e) => setForm((f) => ({ ...f, type: e.target.value as AlertType }))}
                className="input-dark w-full"
              >
                {Object.values(AlertType).map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="label-form">Severity</label>
              <select
                value={form.severity}
                onChange={(e) => setForm((f) => ({ ...f, severity: e.target.value as AlertSeverity }))}
                className="input-dark w-full"
              >
                {Object.values(AlertSeverity).map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Condition + Value */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label-form">Condition</label>
              <select
                value={form.condition}
                onChange={(e) => setForm((f) => ({ ...f, condition: e.target.value }))}
                className="input-dark w-full"
              >
                <option value="ABOVE">Price Above</option>
                <option value="BELOW">Price Below</option>
                <option value="CROSSES">Crosses</option>
                <option value="PERCENT_CHANGE">% Change</option>
              </select>
            </div>
            <div>
              <label className="label-form">Value</label>
              <input
                type="number"
                value={form.targetValue || ''}
                onChange={(e) => setForm((f) => ({ ...f, targetValue: parseFloat(e.target.value) }))}
                placeholder="e.g. 22500"
                className="input-dark w-full"
                step="0.05"
              />
            </div>
          </div>

          {/* Title */}
          <div>
            <label className="label-form">Alert Title</label>
            <input
              type="text"
              value={form.title}
              onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
              placeholder="e.g. Nifty breaks 22500"
              className="input-dark w-full"
            />
          </div>

          {/* Message */}
          <div>
            <label className="label-form">Message (optional)</label>
            <textarea
              value={form.message}
              onChange={(e) => setForm((f) => ({ ...f, message: e.target.value }))}
              rows={2}
              placeholder="Add alert details..."
              className="input-dark w-full resize-none"
            />
          </div>

          {/* Notify Channels */}
          <div>
            <label className="label-form">Notify via</label>
            <div className="flex gap-4">
              {[
                { key: 'notifyEmail' as const, label: 'Email' },
                { key: 'notifyPush' as const, label: 'Push' },
                { key: 'notifySms' as const, label: 'SMS' },
              ].map(({ key, label }) => (
                <label key={key} className="flex items-center gap-2 text-sm text-gray-300 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={form[key]}
                    onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.checked }))}
                    className="accent-accent-blue"
                  />
                  {label}
                </label>
              ))}
            </div>
          </div>

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="btn-secondary flex-1">Cancel</button>
            <button type="submit" className="btn-primary flex-1">Create Alert</button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ─── Alert Row ────────────────────────────────────────────────────────────────

interface AlertRowProps {
  alert: Alert;
  onRead: (id: string) => void;
  onDelete: (id: string) => void;
}

const AlertRow: React.FC<AlertRowProps> = ({ alert, onRead, onDelete }) => {
  const cfg = SEVERITY_CONFIG[alert.severity];

  return (
    <div
      className={clsx(
        'flex gap-4 px-4 py-3.5 border-b border-bg-border/50 border-l-4 transition-colors hover:bg-bg-hover',
        cfg.border,
        !alert.isRead && 'bg-bg-hover/30'
      )}
    >
      <div className="pt-0.5 shrink-0">{cfg.icon}</div>
      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2 flex-wrap">
            <span className={clsx('text-xs font-bold border px-1.5 py-0.5 rounded', cfg.badge)}>
              {alert.severity}
            </span>
            <span className="text-xs bg-bg-tertiary text-gray-400 px-1.5 py-0.5 rounded border border-bg-border">
              {alert.type}
            </span>
            {alert.symbol && (
              <span className="text-xs font-bold text-accent-blue-light">{alert.symbol}</span>
            )}
            {!alert.isRead && (
              <span className="w-2 h-2 rounded-full bg-accent-blue animate-pulse" />
            )}
          </div>
          <div className="flex items-center gap-1 shrink-0">
            {!alert.isRead && (
              <button
                onClick={() => onRead(alert.id)}
                className="text-gray-600 hover:text-emerald-400 p-1 rounded transition-colors"
                title="Mark as read"
              >
                <Check size={13} />
              </button>
            )}
            <button
              onClick={() => onDelete(alert.id)}
              className="text-gray-600 hover:text-red-400 p-1 rounded transition-colors"
              title="Delete"
            >
              <X size={13} />
            </button>
          </div>
        </div>
        <p className="text-sm font-semibold text-white mt-1">{alert.title}</p>
        {alert.message && (
          <p className="text-xs text-gray-400 mt-0.5 leading-relaxed">{alert.message}</p>
        )}
        <div className="flex items-center gap-3 mt-1.5">
          <span className="text-xs text-gray-600">{formatRelativeTime(alert.createdAt)}</span>
          {alert.isTriggered && (
            <span className="text-xs text-orange-400 font-semibold">TRIGGERED</span>
          )}
          {alert.targetValue != null && (
            <span className="text-xs text-gray-500">
              Target: <span className="text-gray-300 font-mono">{alert.targetValue.toLocaleString('en-IN')}</span>
            </span>
          )}
        </div>
      </div>
    </div>
  );
};

// ─── Main Alerts Component ────────────────────────────────────────────────────

export const Alerts: React.FC = () => {
  const { alerts, setAlerts, markAlertRead, markAllAlertsRead, deleteAlert, unreadAlertsCount } =
    useTradingStore();
  const [activeTab, setActiveTab] = useState<FilterTab>('ALL');
  const [showCreate, setShowCreate] = useState(false);
  const [loading, setLoading] = useState(false);

  const fetchAlerts = useCallback(async () => {
    setLoading(true);
    try {
      const data = await alertsApi.getAlerts();
      setAlerts(data);
    } catch {
      toast.error('Failed to load alerts');
    } finally {
      setLoading(false);
    }
  }, [setAlerts]);

  useEffect(() => {
    fetchAlerts();
    const interval = setInterval(fetchAlerts, 30000);
    return () => clearInterval(interval);
  }, [fetchAlerts]);

  const handleMarkRead = useCallback(async (id: string) => {
    markAlertRead(id);
    try {
      await alertsApi.markAsRead(id);
    } catch {
      toast.error('Failed to mark as read');
    }
  }, [markAlertRead]);

  const handleMarkAllRead = useCallback(async () => {
    markAllAlertsRead();
    try {
      await alertsApi.markAllAsRead();
      toast.success('All alerts marked as read');
    } catch {
      toast.error('Failed to mark all as read');
    }
  }, [markAllAlertsRead]);

  const handleDelete = useCallback(async (id: string) => {
    deleteAlert(id);
    try {
      await alertsApi.deleteAlert(id);
    } catch {
      toast.error('Failed to delete alert');
    }
  }, [deleteAlert]);

  const handleCreate = useCallback(async (req: CreateAlertRequest) => {
    try {
      const created = await alertsApi.createAlert(req);
      useTradingStore.getState().addAlert(created);
      toast.success(`Alert created: ${req.title}`);
      setShowCreate(false);
    } catch {
      toast.error('Failed to create alert');
    }
  }, []);

  const filteredAlerts = alerts.filter((a) => {
    if (activeTab === 'ALL') return true;
    if (activeTab === 'TRIGGERED') return a.isTriggered;
    return a.type === activeTab;
  });

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div>
            <h1 className="text-lg font-bold text-white flex items-center gap-2">
              Alerts Center
              {unreadAlertsCount > 0 && (
                <span className="bg-red-500 text-white text-xs px-2 py-0.5 rounded-full font-bold">
                  {unreadAlertsCount}
                </span>
              )}
            </h1>
            <p className="text-xs text-gray-500">{alerts.length} total alerts</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {unreadAlertsCount > 0 && (
            <button
              onClick={handleMarkAllRead}
              className="btn-secondary flex items-center gap-2 text-sm"
            >
              <CheckCheck size={14} />
              Mark All Read
            </button>
          )}
          <button
            onClick={() => setShowCreate(true)}
            className="btn-primary flex items-center gap-2 text-sm"
          >
            <Plus size={14} />
            Create Alert
          </button>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="flex items-center gap-1 bg-bg-tertiary rounded-lg p-1 w-fit border border-bg-border">
        <Filter size={13} className="text-gray-500 ml-2 mr-1" />
        {FILTER_TABS.map(({ value, label }) => {
          const count =
            value === 'ALL'
              ? alerts.length
              : value === 'TRIGGERED'
              ? alerts.filter((a) => a.isTriggered).length
              : alerts.filter((a) => a.type === value).length;
          return (
            <button
              key={value}
              onClick={() => setActiveTab(value)}
              className={clsx(
                'px-3 py-1.5 text-xs font-semibold rounded transition-colors',
                activeTab === value ? 'tab-active' : 'tab-inactive'
              )}
            >
              {label}
              {count > 0 && (
                <span className="ml-1 text-xs opacity-70">({count})</span>
              )}
            </button>
          );
        })}
      </div>

      {/* Alerts List */}
      <div className="bg-bg-card border border-bg-border rounded-xl overflow-hidden">
        {loading && alerts.length === 0 ? (
          <div className="flex items-center justify-center h-40 text-gray-600 text-sm">
            Loading alerts...
          </div>
        ) : filteredAlerts.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-40 gap-3">
            <BellOff size={28} className="text-gray-700" />
            <span className="text-gray-600 text-sm">No alerts in this category</span>
            <button onClick={() => setShowCreate(true)} className="btn-primary text-sm">
              Create First Alert
            </button>
          </div>
        ) : (
          <div>
            {filteredAlerts.map((alert) => (
              <AlertRow
                key={alert.id}
                alert={alert}
                onRead={handleMarkRead}
                onDelete={handleDelete}
              />
            ))}
          </div>
        )}
      </div>

      {/* Create Alert Modal */}
      {showCreate && (
        <CreateAlertModal onClose={() => setShowCreate(false)} onCreate={handleCreate} />
      )}
    </div>
  );
};

export default Alerts;
