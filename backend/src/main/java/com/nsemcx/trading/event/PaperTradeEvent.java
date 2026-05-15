package com.nsemcx.trading.event;

import com.nsemcx.trading.model.PaperTrade.PaperExitReason;
import com.nsemcx.trading.model.PaperTrade.PaperTradeDirection;
import com.nsemcx.trading.model.PaperTrade.PaperTradeStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Domain event / DTO emitted when a paper-trade changes state.
 * Published to WebSocket topic /topic/paper-trades and optionally to Kafka.
 */
public record PaperTradeEvent(

        /** Unique ID of the PaperTrade entity. */
        Long tradeId,

        /** Session that owns this trade. */
        String sessionId,

        /** Instrument symbol (e.g. "RELIANCE.NS"). */
        String symbol,

        /** Human-readable instrument name. */
        String instrumentName,

        /** LONG or SHORT. */
        PaperTradeDirection direction,

        /** Number of units. */
        int quantity,

        /** Price at which the position was opened. */
        BigDecimal entryPrice,

        /** Price at which the position was closed (null if still open). */
        BigDecimal exitPrice,

        /** Current unrealised (OPEN) or realised (CLOSED) P&L. */
        BigDecimal pnl,

        /** P&L as percentage of invested capital. */
        BigDecimal pnlPercent,

        /** Commission charged for this trade. */
        BigDecimal commission,

        /** Current trade status. */
        PaperTradeStatus status,

        /** Why the position was closed (null if still open). */
        PaperExitReason exitReason,

        /** Timestamp of the event. */
        OffsetDateTime eventTime,

        /** Type of event: OPENED / UPDATED / CLOSED / CANCELLED. */
        EventType eventType,

        /** Rolling P&L summary for the session at the time of this event. */
        SessionSummary sessionSummary

) {

    public enum EventType {
        OPENED,
        UPDATED,
        CLOSED,
        CANCELLED
    }

    /**
     * Lightweight session snapshot attached to every event so the UI can
     * update its portfolio panel without a separate API call.
     */
    public record SessionSummary(
            String sessionId,
            BigDecimal capitalRemaining,
            BigDecimal totalUnrealizedPnl,
            BigDecimal totalRealizedPnl,
            long openPositions,
            long closedTrades
    ) {}

    // ── Factory helpers ──────────────────────────────────────────────────────

    public static PaperTradeEvent opened(
            Long tradeId, String sessionId, String symbol, String instrumentName,
            PaperTradeDirection direction, int quantity, BigDecimal entryPrice,
            BigDecimal commission, SessionSummary sessionSummary) {

        return new PaperTradeEvent(
                tradeId, sessionId, symbol, instrumentName, direction,
                quantity, entryPrice, null,
                BigDecimal.ZERO, BigDecimal.ZERO, commission,
                PaperTradeStatus.OPEN, null,
                OffsetDateTime.now(), EventType.OPENED, sessionSummary);
    }

    public static PaperTradeEvent closed(
            Long tradeId, String sessionId, String symbol, String instrumentName,
            PaperTradeDirection direction, int quantity,
            BigDecimal entryPrice, BigDecimal exitPrice,
            BigDecimal pnl, BigDecimal pnlPercent, BigDecimal commission,
            PaperExitReason exitReason, SessionSummary sessionSummary) {

        return new PaperTradeEvent(
                tradeId, sessionId, symbol, instrumentName, direction,
                quantity, entryPrice, exitPrice, pnl, pnlPercent, commission,
                PaperTradeStatus.CLOSED, exitReason,
                OffsetDateTime.now(), EventType.CLOSED, sessionSummary);
    }

    public static PaperTradeEvent updated(
            Long tradeId, String sessionId, String symbol, String instrumentName,
            PaperTradeDirection direction, int quantity,
            BigDecimal entryPrice, BigDecimal pnl, BigDecimal pnlPercent,
            SessionSummary sessionSummary) {

        return new PaperTradeEvent(
                tradeId, sessionId, symbol, instrumentName, direction,
                quantity, entryPrice, null, pnl, pnlPercent, BigDecimal.ZERO,
                PaperTradeStatus.OPEN, null,
                OffsetDateTime.now(), EventType.UPDATED, sessionSummary);
    }
}
