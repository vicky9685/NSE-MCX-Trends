package com.nsemcx.trading.websocket;

import com.nsemcx.trading.event.BacktestProgress;
import com.nsemcx.trading.event.MarketDashboard;
import com.nsemcx.trading.event.PaperTradeEvent;
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

    private static final String TOPIC_MARKET_DATA  = "/topic/market-data";
    private static final String TOPIC_SIGNALS      = "/topic/signals";
    private static final String TOPIC_ALERTS       = "/topic/alerts";
    private static final String TOPIC_DASHBOARD    = "/topic/dashboard";
    private static final String TOPIC_PAPER_TRADES = "/topic/paper-trades";
    private static final String TOPIC_BACKTEST     = "/topic/backtest";

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
            log.debug("Broadcast dashboard snapshot sentimentScore={}", dashboard.marketSentimentScore());
        } catch (Exception ex) {
            log.error("Failed to broadcast dashboard: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Broadcasts a paper-trading execution event to all subscribers of
     * {@code /topic/paper-trades}.
     *
     * @param event  paper-trade lifecycle event (opened, updated, closed)
     */
    public void broadcastPaperTrade(PaperTradeEvent event) {
        try {
            messagingTemplate.convertAndSend(TOPIC_PAPER_TRADES, event);
            log.debug("Broadcast paper-trade event type={} symbol={}",
                    event.eventType(), event.symbol());
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
            log.debug("Broadcast backtest progress id={} pct={}%",
                    progress.backtestId(), progress.progressPercent());
        } catch (Exception ex) {
            log.error("Failed to broadcast backtest progress: {}", ex.getMessage(), ex);
        }
    }
}
