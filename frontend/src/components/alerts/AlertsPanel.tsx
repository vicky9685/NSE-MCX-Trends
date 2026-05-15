import React, { useState, useEffect, useCallback } from 'react';
import { Bell, BellOff, Plus, Trash2, Check, CheckCheck, AlertTriangle, TrendingUp, Newspaper, Shield } from 'lucide-react';
import { clsx } from 'clsx';
import { useTradingStore } from '@/store/tradingStore';
import { alertsApi } from '@/api/portfolio';
import { formatRelativeTime, formatDateTime } from '@/utils/formatters';
import { AlertType, AlertSeverity, Exchange } from '@/types';
import type { Alert, CreateAlertRequest } from '@/types';

// ─── Alert Type Icon ──────────────────────────────────────────────────────────

function AlertTypeIcon({ type }: { type: AlertType }) {
  switch (type) {
    case AlertType.PRICE:
      return <TrendingUp size={14} className="text-blue-400" />;
    case AlertType.TECHNICAL:
      return <TrendingUp size={14} className="text-purple-400" />;
    case AlertType.NEWS:
      return <Newspaper size={14} className="text-yellow-400" />;
    case AlertType.RISK:
      return <Shield size={14} className="text-red-400" />;
    case AlertType.SIGNAL:
      return <Bell size={14} className="text-emerald-400" />;
    default:
      return <Bell size={14} className="text-gray-400" />;
  }
}

// ─── Alert Row ────────────────────────────────────────────────────────────────

function AlertRow({
  alert,
  onDelete,
  onToggle,
  onMarkRead,
}: {
  alert: Alert;
  onDelete: (id: string) => void;
  onToggle: (id: string, active: boolean) => void;
  onMarkRead: (id: string) => void;
}) {
  const severityBorder =
    alert.severity === AlertSeverity.CRITICAL
      ? 'border-red-500/40'
      : alert.severity === AlertSeverity.WARNING
      ? 'border-yellow-500/40'
      : 'border-bg-border';

  const severityBg =
    alert.severity === AlertSeverity.CRITICAL
      ? 'bg-red-500/5'
      : alert.severity === AlertSeverity.WARNING
      ? 'bg-yellow-500/5'
      : '';

  return (
    <div
      className={clsx(
        'rounded-xl border p-3 flex items-start gap-3 transition-all duration-200',
        severityBorder,
        severityBg,
        !alert.isRead ? 'bg-accent-blue/5' : 'bg-bg-card'
      )}
    >
      <div className="mt-0.5 shrink-0">
        <AlertTypeIcon type={alert.type} />
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <span className="font-semibold text-white text-sm">{alert.title}</span>
              {alert.symbol && (
                <span className="text-xs font-mono text-gray-400">{alert.symbol}</span>
              )}
              {!alert.isRead && (
                <span className="w-1.5 h-1.5 rounded-full bg-accent-blue inline-block" />
              )}
            </div>
            <p className="text-xs text-gray-400 mt-0.5 leading-relaxed">{alert.message}</p>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            {!alert.isRead && (
              <button
                onClick={() => onMarkRead(alert.id)}
                className="text-gray-500 hover:text-accent-blue transition-colors"
                title="Mark as read"
              >
                <Check size={13} />
              </button>
            )}
            <button
              onClick={() => onToggle(alert.id, !alert.isActive)}
              className={clsx(
                'transition-colors',
                alert.isActive ? 'text-emerald-400 hover:text-gray-400' : 'text-gray-600 hover:text-emerald-400'
              )}
              title={alert.isActive ? 'Disable' : 'Enable'}
            >
              {alert.isActive ? <Bell size={13} /> : <BellOff size={13} />}
            </button>
            <button
              onClick={() => onDelete(alert.id)}
              className="text-gray-600 hover:text-red-400 transition-colors"
            >
              <Trash2 size={13} />
            </button>
          </div>
        </div>

        <div className="flex items-center gap-3 mt-1.5 flex-wrap">
          <span
            className={clsx(
              'text-xs px-1.5 py-0.5 rounded font-semibold',
              alert.severity === AlertSeverity.CRITICAL
                ? 'bg-red-500/20 text-red-400'
                : alert.severity === AlertSeverity.WARNING
                ? 'bg-yellow-500/20 text-yellow-400'
                : 'bg-gray-600/30 text-gray-400'
            )}
          >
            {alert.severity}
          </span>
          <span className="text-xs text-gray-600">{alert.type}</span>
          {alert.isTriggered && (
            <span className="text-xs text-orange-400">
              Triggered: {alert.triggeredAt ? formatRelativeTime(alert.triggeredAt) : ''}
            </span>
          )}
          <span className="text-xs text-gray-600 ml-auto">
            {formatRelativeTime(alert.createdAt)}
          </span>
        </div>
      </div>
    </div>
  );
}

// ─── Create Alert Form ────────────────────────────────────────────────────────

function CreateAlertForm({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<Partial<CreateAlertRequest>>({
    type: AlertType.PRICE,
    severity: AlertSeverity.INFO,
    title: '',
    message: '',
    symbol: '',
    exchange: Exchange.NSE,
    condition: '',
    targetValue: undefined,
    notifyEmail: false,
    notifyPush: true,
    notifySms: false,
  });

  const inputClass =
    'w-full bg-bg-tertiary border border-bg-border rounded-lg px-3 py-2 text-sm text-white focus:border-accent-blue focus:outline-none transition-colors';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title || !form.message) return;
    setLoading(true);
    try {
      await alertsApi.createAlert(form as CreateAlertRequest);
      onCreated();
      setOpen(false);
      setForm((f) => ({ ...f, title: '', message: '', symbol: '', condition: '', targetValue: undefined }));
    } catch (err) {
      console.error('[CreateAlert] error:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 bg-accent-blue hover:bg-accent-blue-dark text-white px-4 py-2 rounded-lg text-sm font-semibold transition-colors"
      >
        <Plus size={14} />
        Create Alert
      </button>

      {open && (
        <div className="mt-4 rounded-xl bg-bg-card border border-bg-border p-4 animate-fade-in">
          <h3 className="font-semibold text-white text-sm mb-4">New Alert</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Alert Type</label>
              <select
                className={inputClass}
                value={form.type}
                onChange={(e) => setForm((f) => ({ ...f, type: e.target.value as AlertType }))}
              >
                {Object.values(AlertType).map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Severity</label>
              <select
                className={inputClass}
                value={form.severity}
                onChange={(e) => setForm((f) => ({ ...f, severity: e.target.value as AlertSeverity }))}
              >
                {Object.values(AlertSeverity).map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
            <div className="col-span-2">
              <label className="text-xs text-gray-400 mb-1 block">Title *</label>
              <input
                className={inputClass}
                placeholder="Alert title"
                value={form.title}
                onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
                required
              />
            </div>
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Symbol</label>
              <input
                className={inputClass}
                placeholder="e.g. RELIANCE"
                value={form.symbol}
                onChange={(e) => setForm((f) => ({ ...f, symbol: e.target.value.toUpperCase() }))}
              />
            </div>
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Exchange</label>
              <select
                className={inputClass}
                value={form.exchange}
                onChange={(e) => setForm((f) => ({ ...f, exchange: e.target.value as Exchange }))}
              >
                {Object.values(Exchange).map((ex) => (
                  <option key={ex} value={ex}>{ex}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Condition</label>
              <input
                className={inputClass}
                placeholder="e.g. ABOVE, BELOW"
                value={form.condition}
                onChange={(e) => setForm((f) => ({ ...f, condition: e.target.value }))}
              />
            </div>
            <div>
              <label className="text-xs text-gray-400 mb-1 block">Target Value</label>
              <input
                className={inputClass}
                type="number"
                step="0.01"
                placeholder="0.00"
                value={form.targetValue ?? ''}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    targetValue: e.target.value ? parseFloat(e.target.value) : undefined,
                  }))
                }
              />
            </div>
            <div className="col-span-2">
              <label className="text-xs text-gray-400 mb-1 block">Message *</label>
              <textarea
                className={clsx(inputClass, 'resize-none')}
                rows={2}
                placeholder="Alert message"
                value={form.message}
                onChange={(e) => setForm((f) => ({ ...f, message: e.target.value }))}
                required
              />
            </div>

            {/* Notification Toggles */}
            <div className="col-span-2 flex items-center gap-4">
              <span className="text-xs text-gray-400">Notify via:</span>
              {(['notifyPush', 'notifyEmail', 'notifySms'] as const).map((key) => (
                <label key={key} className="flex items-center gap-1.5 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={form[key] ?? false}
                    onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.checked }))}
                    className="rounded"
                  />
                  <span className="text-xs text-gray-300 capitalize">
                    {key.replace('notify', '')}
                  </span>
                </label>
              ))}
            </div>

            <div className="col-span-2 flex gap-2 justify-end">
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="px-4 py-2 text-sm text-gray-400 hover:text-white border border-bg-border rounded-lg transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={loading}
                className="px-4 py-2 text-sm bg-accent-blue hover:bg-accent-blue-dark text-white rounded-lg font-semibold transition-colors disabled:opacity-50"
              >
                {loading ? 'Creating...' : 'Create Alert'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

// ─── Main Panel ───────────────────────────────────────────────────────────────

const ALERT_TYPE_FILTERS = ['ALL', ...Object.values(AlertType)] as const;

export const AlertsPanel: React.FC = () => {
  const { alerts, unreadAlertsCount, setAlerts, updateAlert, deleteAlert, markAlertRead, markAllAlertsRead, setAlertsLoading } =
    useTradingStore();
  const [filter, setFilter] = useState<string>('ALL');
  const [showTriggered, setShowTriggered] = useState(false);

  const fetchAlerts = useCallback(async () => {
    setAlertsLoading(true);
    try {
      const data = await alertsApi.getAlerts();
      setAlerts(data);
    } catch (err) {
      console.error('[AlertsPanel] fetch error:', err);
    } finally {
      setAlertsLoading(false);
    }
  }, [setAlerts, setAlertsLoading]);

  useEffect(() => {
    fetchAlerts();
  }, [fetchAlerts]);

  const handleDelete = async (id: string) => {
    try {
      await alertsApi.deleteAlert(id);
      deleteAlert(id);
    } catch (err) {
      console.error('[AlertsPanel] delete error:', err);
    }
  };

  const handleToggle = async (id: string, active: boolean) => {
    try {
      await alertsApi.updateAlert(id, { isActive: active });
      updateAlert(id, { isActive: active });
    } catch (err) {
      console.error('[AlertsPanel] toggle error:', err);
    }
  };

  const handleMarkRead = async (id: string) => {
    try {
      await alertsApi.markAsRead(id);
      markAlertRead(id);
    } catch (err) {
      markAlertRead(id);
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await alertsApi.markAllAsRead();
      markAllAlertsRead();
    } catch (err) {
      markAllAlertsRead();
    }
  };

  const filtered = alerts.filter((a) => {
    if (filter !== 'ALL' && a.type !== filter) return false;
    if (showTriggered) return a.isTriggered;
    return true;
  });

  return (
    <div className="space-y-4">
      {/* Header Actions */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="flex items-center gap-2">
          <Bell size={18} className="text-accent-blue" />
          <h2 className="font-semibold text-white">Alerts Center</h2>
          {unreadAlertsCount > 0 && (
            <span className="bg-accent-blue text-white text-xs px-1.5 py-0.5 rounded-full font-bold">
              {unreadAlertsCount}
            </span>
          )}
        </div>

        {unreadAlertsCount > 0 && (
          <button
            onClick={handleMarkAllRead}
            className="flex items-center gap-1 text-xs text-gray-400 hover:text-white transition-colors"
          >
            <CheckCheck size={12} />
            Mark all read
          </button>
        )}

        <label className="flex items-center gap-1.5 cursor-pointer ml-auto">
          <input
            type="checkbox"
            checked={showTriggered}
            onChange={(e) => setShowTriggered(e.target.checked)}
          />
          <span className="text-xs text-gray-400">Show triggered only</span>
        </label>

        <CreateAlertForm onCreated={fetchAlerts} />
      </div>

      {/* Type Filter */}
      <div className="flex items-center gap-1 flex-wrap">
        {ALERT_TYPE_FILTERS.map((t) => (
          <button
            key={t}
            onClick={() => setFilter(t)}
            className={clsx(
              'text-xs px-3 py-1 rounded-lg transition-colors',
              filter === t
                ? 'bg-accent-blue text-white'
                : 'text-gray-400 hover:text-white bg-bg-card border border-bg-border hover:border-accent-blue'
            )}
          >
            {t}
          </button>
        ))}
      </div>

      {/* Alerts List */}
      <div className="space-y-2">
        {filtered.length === 0 ? (
          <div className="text-center py-12 text-gray-500">
            <Bell size={32} className="mx-auto mb-2 opacity-30" />
            <div>No alerts found</div>
          </div>
        ) : (
          filtered.map((alert) => (
            <AlertRow
              key={alert.id}
              alert={alert}
              onDelete={handleDelete}
              onToggle={handleToggle}
              onMarkRead={handleMarkRead}
            />
          ))
        )}
      </div>
    </div>
  );
};

export default AlertsPanel;
