import apiClient from './client';
import type {
  PortfolioPosition,
  PortfolioSummary,
  PerformanceDataPoint,
  RiskMetrics,
  Alert,
  CreateAlertRequest,
  BrokerConfig,
  BrokerType,
} from '@/types';

export const portfolioApi = {
  // Portfolio summary
  getSummary: () =>
    apiClient.get<PortfolioSummary>('/api/portfolio/summary').then((r) => r.data),

  // All positions
  getPositions: () =>
    apiClient.get<PortfolioPosition[]>('/api/portfolio/positions').then((r) => r.data),

  // Add a position manually
  addPosition: (position: Partial<PortfolioPosition>) =>
    apiClient.post<PortfolioPosition>('/api/portfolio/positions', position).then((r) => r.data),

  // Delete a position
  deletePosition: (id: string) =>
    apiClient.delete(`/api/portfolio/positions/${id}`).then((r) => r.data),

  // Performance chart data vs Nifty
  getPerformance: (days: number = 90) =>
    apiClient
      .get<PerformanceDataPoint[]>('/api/portfolio/performance', { params: { days } })
      .then((r) => r.data),

  // Risk metrics
  getRiskMetrics: () =>
    apiClient.get<RiskMetrics>('/api/portfolio/risk').then((r) => r.data),
};

export const alertsApi = {
  // Get all alerts
  getAlerts: () =>
    apiClient.get<Alert[]>('/api/alerts').then((r) => r.data),

  // Create a new alert
  createAlert: (req: CreateAlertRequest) =>
    apiClient.post<Alert>('/api/alerts', req).then((r) => r.data),

  // Update alert (toggle active)
  updateAlert: (id: string, updates: Partial<Alert>) =>
    apiClient.patch<Alert>(`/api/alerts/${id}`, updates).then((r) => r.data),

  // Delete an alert
  deleteAlert: (id: string) =>
    apiClient.delete(`/api/alerts/${id}`).then((r) => r.data),

  // Mark alert as read
  markAsRead: (id: string) =>
    apiClient.patch(`/api/alerts/${id}/read`).then((r) => r.data),

  // Mark all alerts as read
  markAllAsRead: () =>
    apiClient.patch('/api/alerts/read-all').then((r) => r.data),
};

export const brokerApi = {
  // Get all broker configs
  getBrokers: () =>
    apiClient.get<BrokerConfig[]>('/api/broker/configs').then((r) => r.data),

  // Save broker config
  saveBrokerConfig: (config: Partial<BrokerConfig>) =>
    apiClient.post<BrokerConfig>('/api/broker/config', config).then((r) => r.data),

  // Connect to broker
  connectBroker: (broker: BrokerType) =>
    apiClient.post<{ success: boolean; message: string }>(`/api/broker/${broker}/connect`).then((r) => r.data),

  // Disconnect from broker
  disconnectBroker: (broker: BrokerType) =>
    apiClient.post<{ success: boolean }>(`/api/broker/${broker}/disconnect`).then((r) => r.data),

  // Test broker connection
  testConnection: (broker: BrokerType) =>
    apiClient
      .get<{ connected: boolean; latency: number }>(`/api/broker/${broker}/test`)
      .then((r) => r.data),
};
