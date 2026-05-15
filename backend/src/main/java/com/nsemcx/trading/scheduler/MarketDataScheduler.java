package com.nsemcx.trading.scheduler;

import com.nsemcx.trading.agent.OrchestratorAgent;
import com.nsemcx.trading.config.TradingProperties;
import com.nsemcx.trading.service.AlertService;
import com.nsemcx.trading.service.PaperTradingService;
import com.nsemcx.trading.service.TechnicalAnalysisService;
import com.nsemcx.trading.service.YahooFinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Scheduled tasks that drive the market-data pipeline.
 *
 * <p>All cron expressions and fixed rates are configurable via
 * {@code trading.scheduler.*} in {@code application.yml}.
 *
 * <p>Tasks that are market-hours sensitive call {@link #isMarketOpen()} before
 * performing any work.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataScheduler {

    private final TradingProperties tradingProperties;
    private final YahooFinanceService yahooFinanceService;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final OrchestratorAgent orchestratorAgent;
    private final AlertService alertService;
    private final PaperTradingService paperTradingService;

    // ── Historical data refresh ───────────────────────────────────────────────

    /**
     * Fetches 1-year historical OHLCV data for all configured instruments.
     * Runs once per trading day at 06:30 IST so the database is current before
     * the market opens at 09:15.
     *
     * <p>Cron: {@code 0 30 6 * * MON-FRI} (configurable via
     * {@code trading.scheduler.historical-cron}).
     */
    @Scheduled(cron = "${trading.scheduler.historical-cron:0 30 6 * * MON-FRI}",
               zone   = "Asia/Kolkata")
    public void refreshHistoricalData() {
        log.info("[Scheduler] refreshHistoricalData – fetching 1y history for all instruments");
        try {
            // Delegate to YahooFinanceService which iterates all active instruments
            yahooFinanceService.refreshAllInstruments();
            log.info("[Scheduler] refreshHistoricalData – complete");
        } catch (Exception ex) {
            log.error("[Scheduler] refreshHistoricalData failed: {}", ex.getMessage(), ex);
        }
    }

    // ── Technical analysis ────────────────────────────────────────────────────

    /**
     * Computes technical indicators for all active instruments.
     * Only runs during market hours to avoid unnecessary computation.
     *
     * <p>Rate: every 15 minutes (configurable via
     * {@code trading.scheduler.technical-rate-ms}, default 900 000 ms).
     */
    @Scheduled(fixedRateString = "${trading.scheduler.technical-rate-ms:900000}")
    public void calculateTechnicals() {
        if (!isMarketOpen()) {
            log.trace("[Scheduler] calculateTechnicals – market closed, skipping");
            return;
        }

        log.info("[Scheduler] calculateTechnicals – running for all active instruments");
        try {
            technicalAnalysisService.analyzeAllActive();
            log.info("[Scheduler] calculateTechnicals – complete");
        } catch (Exception ex) {
            log.error("[Scheduler] calculateTechnicals failed: {}", ex.getMessage(), ex);
        }
    }

    // ── Signal generation ─────────────────────────────────────────────────────

    /**
     * Runs the full multi-agent signal generation pipeline.
     *
     * <p>Rate: every 30 minutes (configurable via
     * {@code trading.scheduler.signal-rate-ms}, default 1 800 000 ms).
     */
    @Scheduled(fixedRateString = "${trading.scheduler.signal-rate-ms:1800000}")
    public void generateSignals() {
        log.info("[Scheduler] generateSignals – starting orchestrator");
        try {
            orchestratorAgent.generateAllSignals();
            log.info("[Scheduler] generateSignals – complete");
        } catch (Exception ex) {
            log.error("[Scheduler] generateSignals failed: {}", ex.getMessage(), ex);
        }
    }

    // ── Alert evaluation ──────────────────────────────────────────────────────

    /**
     * Evaluates all active price and technical alerts against the latest prices.
     *
     * <p>Rate: every 60 seconds (configurable via
     * {@code trading.scheduler.alert-rate-ms}, default 60 000 ms).
     */
    @Scheduled(fixedRateString = "${trading.scheduler.alert-rate-ms:60000}")
    public void checkAlerts() {
        log.debug("[Scheduler] checkAlerts – evaluating active alerts");
        try {
            alertService.checkAllAlerts();
        } catch (Exception ex) {
            log.error("[Scheduler] checkAlerts failed: {}", ex.getMessage(), ex);
        }
    }

    // ── Paper portfolio update ────────────────────────────────────────────────

    /**
     * Updates open paper-trading positions against the latest market prices.
     * Closes positions where target or stop-loss has been hit.
     *
     * <p>Rate: every 60 seconds (configurable via
     * {@code trading.scheduler.paper-rate-ms}, default 60 000 ms).
     */
    @Scheduled(fixedRateString = "${trading.scheduler.paper-rate-ms:60000}")
    public void updatePaperPortfolio() {
        if (!tradingProperties.paperTrading().enabled()) {
            return;
        }
        log.debug("[Scheduler] updatePaperPortfolio – updating open positions");
        try {
            paperTradingService.updatePositions();
        } catch (Exception ex) {
            log.error("[Scheduler] updatePaperPortfolio failed: {}", ex.getMessage(), ex);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if either NSE (09:15–15:30 IST) or MCX (09:00–23:30 IST)
     * is currently open, based on the configured market hours and IST timezone.
     */
    public boolean isMarketOpen() {
        TradingProperties.MarketHours hours = tradingProperties.marketHours();
        ZoneId ist = ZoneId.of(hours.timezone());
        LocalTime now = ZonedDateTime.now(ist).toLocalTime();

        LocalTime nseOpen  = LocalTime.parse(hours.nseOpen());
        LocalTime nseClose = LocalTime.parse(hours.nseClose());
        LocalTime mcxOpen  = LocalTime.parse(hours.mcxOpen());
        LocalTime mcxClose = LocalTime.parse(hours.mcxClose());

        boolean nseOpen_ = !now.isBefore(nseOpen) && !now.isAfter(nseClose);
        boolean mcxOpen_ = !now.isBefore(mcxOpen) && !now.isAfter(mcxClose);

        return nseOpen_ || mcxOpen_;
    }
}
