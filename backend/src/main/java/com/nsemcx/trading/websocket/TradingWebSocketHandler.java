package com.nsemcx.trading.websocket;

import com.nsemcx.trading.model.Alert;
import com.nsemcx.trading.model.MarketData;
import com.nsemcx.trading.model.TradeSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service that broadcasts domain events to WebSocket subscribers via STOMP.
 *
 * <p>Topic routing:
 * <pre>
 *  /topic/market-data   – OHLCV candle updates
 *  /topic/signals       – generated trade signals
 *  /topic/alerts        – price / technical alerts
 *  /topic/dashboard     – aggregated market dashboard snapshot
 *  /topic/paper-trades  – paper-trading execution events
 *  /topic/backtest      – backtest progress / completion events
 * </pre>
 *
 * <p>All methods are fire-and-forget; exceptions are caught and logged so a single
 * failed broadcast never disrupts the calling thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingWebSocketHandler {

    private static final String TOPIC_MARKET_DATA   = "/topic/market-data";
    private static final String TOPIC_SIGNALS       = "/topic/signals";
    private static final String TOPIC_ALERTS        = "/topic/alerts";
    private static final String TOPIC_DASHBOARD     = "/topic/dashboard";
    private static final String TOPIC_PAPER_TRADES  = "/topic/paper-trades";
    private static final String TOPIC_BACKTEST      = "/topic/backtest";

    private final SimpMessagingTemplate messagingTemplate;

    // ── Broadcast methods ─────────────────────────────────────────────────────

    /**
     * Broadcasts a real-time OHLCV candle update to all subscribers of
     * {@code /topic/market-data}.
     */
    public void broadcastMarketData(MarketData data) {
        try {
            messagingTemplate.convertAndSend(TOPIC_MARKET_DATA, data);
            log.debug("Broadcast market-data for instrument id={}", data.getInstrument().getId());
        } catch (Exception ex) {
            log.error("Failed to broadcast market-data: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Broadcasts a new or updated {@link TradeSignal} to all subscribers of
     * {@code /topic/signals}.
     */
    public void broadcastSignal(TradeSignal signal) {
        try {
            messagingTemplate.convertAndSend(TOPIC_SIGNALS, signal);
            log.debug("Broadcast signal id={} type={} instrument={}",
                    signal.getId(), signal.getSignalType(),
                    signal.getInstrument().getSymbol());
        } catch (Exception ex) {
            log.error("Failed to broadcast signal id={}: {}", signal.getId(), ex.getMessage(), ex);
        }
    }

    /**
     * Broadcasts a triggered {@link Alert} to all subscribers of
     * {@code /topic/alerts}.
     */
    public void broadcastAlert(Alert alert) {
        try {
            messagingTemplate.convertAndSend(TOPIC_ALERTS, alert);
            log.debug("Broadcast alert id={} type={}", alert.getId(), alert.getAlertType());
        } catch (Exception ex) {
            log.error("Failed to broadcast alert id={}: {}", alert.getId(), ex.getMessage(), ex);
        }
    }

    /**
     * Broadcasts a {@link MarketDashboard} snapshot to all subscribers of
     * {@code /topic/dashboard}.
     */
    public void broadcastDashboard(MarketDashboard dashboard) {
        try {
            messagingTemplate.convertAndSend(TOPIC_DASHBOARD, dashboard);
            log.debug("Broadcast dashboard snapshot sentimentScore={}", dashboard.sentimentScore());
        } catch (Exception ex) {
            log.error("Failed to broadcast dashboard: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Broadcasts a paper-trading execution event to all subscribers of
     * {@code /topic/paper-trades}.
     *
     * @param event  any serialisable paper-trade event (entry, exit, position update)
     */
    public void broadcastPaperTrade(PaperTradeEvent event) {
        try {
            messagingTemplate.convertAndSend(TOPIC_PAPER_TRADES, event);
            log.debug("Broadcast paper-trade event type={} instrument={}",
                    event.eventType(), event.instrumentSymbol());
        } catch (Exception ex) {
            log.error("Failed to broadcast paper-trade event: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Broadcasts a backtest progress update to all subscribers of
     * {@code /topic/backtest}.
     */
    public void broadcastBacktestProgress(BacktestProgress progress) {
        try {
            messagingTemplate.convertAndSend(TOPIC_BACKTEST, progress);
            log.debug("Broadcast backtest progress runId={} pct={}%",
                    progress.runId(), progress.percentComplete());
        } catch (Exception ex) {
            log.error("Failed to broadcast backtest progress: {}", ex.getMessage(), ex);
        }
    }

    // ── Inner records (payload types) ─────────────────────────────────────────

    /**
     * Aggregated market dashboard snapshot broadcast to connected clients.
     *
     * @param sentimentScore          composite market sentiment score (-100 to +100)
     * @param vixOutlook              textual interpretation of India VIX level
     * @param institutionalFlow       FII/DII flow assessment
     * @param sectorLeaders           top performing sectors with score
     * @param sectorLaggards          worst performing sectors with score
     * @param topOpportunities        highest-confidence active trade signals
     * @param topRisks                key risk factors in plain English
     * @param capitalProtectionStrategy recommended capital-protection approach
     */
    public record MarketDashboard(
            double sentimentScore,
            String vixOutlook,
            String institutionalFlow,
            java.util.List<SectorAnalysis> sectorLeaders,
            java.util.List<SectorAnalysis> sectorLaggards,
            java.util.List<TradeSignal> topOpportunities,
            java.util.List<String> topRisks,
            String capitalProtectionStrategy
    ) {}

    /** Sector performance summary used in the dashboard. */
    public record SectorAnalysis(
            String sectorName,
            double performanceScore,
            String outlook,
            java.util.List<String> topStocks
    ) {}

    /**
     * Paper-trade lifecycle event.
     *
     * @param eventType        ENTRY, EXIT, UPDATE, RESET
     * @param instrumentSymbol symbol of the traded instrument
     * @param direction        LONG or SHORT
     * @param quantity         number of units
     * @param price            execution price
     * @param pnl              realised or unrealised P&L
     * @param sessionId        paper-trading session identifier
     * @param timestamp        event timestamp
     */
    public record PaperTradeEvent(
            String eventType,
            String instrumentSymbol,
            String direction,
            int quantity,
            java.math.BigDecimal price,
            java.math.BigDecimal pnl,
            String sessionId,
            java.time.OffsetDateTime timestamp
    ) {}

    /**
     * Backtest run progress indicator.
     *
     * @param runId           unique backtest run identifier
     * @param status          RUNNING, COMPLETED, FAILED
     * @param percentComplete 0-100
     * @param currentDate     date currently being processed in the simulation
     * @param totalTrades     number of trades executed so far
     * @param currentEquity   running portfolio equity value
     */
    public record BacktestProgress(
            String runId,
            String status,
            int percentComplete,
            java.time.LocalDate currentDate,
            int totalTrades,
            double currentEquity
    ) {}
}
