package com.nsemcx.trading.event;

import com.nsemcx.trading.model.BacktestResult.BacktestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Progress event emitted during a backtest run.
 * Published to WebSocket topic /topic/backtest/{backtestId} and optionally to Kafka.
 * Clients receive incremental updates without polling the REST API.
 */
public record BacktestProgress(

        /** ID of the BacktestResult entity being computed. */
        Long backtestId,

        /** Human-readable backtest name. */
        String name,

        /** Strategy being tested. */
        String strategyName,

        /** Current backtest status. */
        BacktestStatus status,

        /** Percentage completion 0–100. */
        int progressPercent,

        /** Date currently being simulated. */
        LocalDate currentDate,

        /** Total date range being simulated. */
        LocalDate startDate,
        LocalDate endDate,

        /** Number of trading days processed so far. */
        int daysProcessed,

        /** Total number of trading days in the range. */
        int totalDays,

        /** Number of trades executed so far. */
        int tradesExecuted,

        /** Current running capital. */
        BigDecimal currentCapital,

        /** Running total return percentage. */
        BigDecimal runningReturnPct,

        /** Current max-drawdown so far. */
        BigDecimal currentMaxDrawdownPct,

        /** Error message if status == FAILED. */
        String errorMessage,

        /** Wall-clock time the event was emitted. */
        Instant timestamp

) {

    // ── Factory methods ──────────────────────────────────────────────────────

    public static BacktestProgress started(Long backtestId, String name, String strategyName,
                                           LocalDate startDate, LocalDate endDate, int totalDays) {
        return new BacktestProgress(
                backtestId, name, strategyName,
                BacktestStatus.RUNNING,
                0, startDate, startDate, endDate,
                0, totalDays, 0,
                null, BigDecimal.ZERO, BigDecimal.ZERO,
                null, Instant.now());
    }

    public static BacktestProgress progress(Long backtestId, String name, String strategyName,
                                            LocalDate currentDate, LocalDate startDate, LocalDate endDate,
                                            int daysProcessed, int totalDays,
                                            int tradesExecuted, BigDecimal currentCapital,
                                            BigDecimal runningReturnPct, BigDecimal currentMaxDrawdownPct) {
        int pct = totalDays > 0 ? Math.min(99, (int) ((daysProcessed * 100.0) / totalDays)) : 0;
        return new BacktestProgress(
                backtestId, name, strategyName,
                BacktestStatus.RUNNING,
                pct, currentDate, startDate, endDate,
                daysProcessed, totalDays, tradesExecuted,
                currentCapital, runningReturnPct, currentMaxDrawdownPct,
                null, Instant.now());
    }

    public static BacktestProgress completed(Long backtestId, String name, String strategyName,
                                             LocalDate startDate, LocalDate endDate,
                                             int totalDays, int tradesExecuted,
                                             BigDecimal finalCapital, BigDecimal totalReturnPct,
                                             BigDecimal maxDrawdownPct) {
        return new BacktestProgress(
                backtestId, name, strategyName,
                BacktestStatus.COMPLETED,
                100, endDate, startDate, endDate,
                totalDays, totalDays, tradesExecuted,
                finalCapital, totalReturnPct, maxDrawdownPct,
                null, Instant.now());
    }

    public static BacktestProgress failed(Long backtestId, String name, String strategyName,
                                          LocalDate startDate, LocalDate endDate,
                                          String errorMessage) {
        return new BacktestProgress(
                backtestId, name, strategyName,
                BacktestStatus.FAILED,
                -1, null, startDate, endDate,
                0, 0, 0,
                null, BigDecimal.ZERO, BigDecimal.ZERO,
                errorMessage, Instant.now());
    }

    // ── Derived helpers ──────────────────────────────────────────────────────

    public boolean isTerminal() {
        return status == BacktestStatus.COMPLETED || status == BacktestStatus.FAILED;
    }

    public boolean isFailed() {
        return status == BacktestStatus.FAILED;
    }
}
