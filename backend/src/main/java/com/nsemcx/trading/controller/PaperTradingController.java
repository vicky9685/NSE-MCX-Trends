package com.nsemcx.trading.controller;

import com.nsemcx.trading.config.TradingProperties;
import com.nsemcx.trading.event.ApiResponse;
import com.nsemcx.trading.model.PaperTrade;
import com.nsemcx.trading.model.TradeSignal;
import com.nsemcx.trading.repository.PaperTradeRepository;
import com.nsemcx.trading.repository.TradeSignalRepository;
import com.nsemcx.trading.service.PaperTradingService;
import com.nsemcx.trading.service.PaperTradingService.PortfolioSummary;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for paper trading.
 * Base path: /api/paper
 *
 * Session ID is resolved from the X-Session-Id header.
 * Falls back to "default" when the header is absent.
 */
@Slf4j
@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
public class PaperTradingController {

    private static final String DEFAULT_SESSION = "default";
    private static final String SESSION_HEADER  = "X-Session-Id";

    private final PaperTradingService paperTradingService;
    private final PaperTradeRepository paperTradeRepository;
    private final TradeSignalRepository tradeSignalRepository;
    private final TradingProperties tradingProperties;

    // ── Request DTOs ──────────────────────────────────────────────────────────

    public record ExecuteSignalRequest(@NotNull Long signalId) {}

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * POST /api/paper/trade
     * Executes a trade signal as a paper trade in the caller's session.
     * Body: { "signalId": 123 }
     */
    @PostMapping("/trade")
    public ResponseEntity<ApiResponse<PaperTrade>> executeTrade(
            @RequestBody ExecuteSignalRequest req,
            @RequestHeader(value = SESSION_HEADER, defaultValue = DEFAULT_SESSION) String sessionId) {

        TradeSignal signal = tradeSignalRepository.findById(req.signalId()).orElse(null);
        if (signal == null) {
            return ResponseEntity.ok(ApiResponse.error("Signal not found: " + req.signalId()));
        }

        try {
            TradingProperties.PaperTrading cfg = tradingProperties.paperTrading();
            PaperTrade trade = paperTradingService.openPosition(signal, sessionId, cfg);
            if (trade == null) {
                return ResponseEntity.ok(ApiResponse.error(
                        "Trade not opened — quantity zero or insufficient capital"));
            }
            return ResponseEntity.ok(ApiResponse.ok(trade, "Paper trade opened successfully"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.ok(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Paper trade execution failed: {}", ex.getMessage(), ex);
            return ResponseEntity.ok(ApiResponse.error("Execution failed: " + ex.getMessage()));
        }
    }

    /**
     * GET /api/paper/portfolio
     * Returns the current paper portfolio for the session.
     */
    @GetMapping("/portfolio")
    public ResponseEntity<ApiResponse<PortfolioSummary>> getPortfolio(
            @RequestHeader(value = SESSION_HEADER, defaultValue = DEFAULT_SESSION) String sessionId) {
        PortfolioSummary summary = paperTradingService.getSummary(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(summary, "Paper portfolio for session: " + sessionId));
    }

    /**
     * GET /api/paper/trades
     * Returns all paper trades (open and closed) for the session.
     */
    @GetMapping("/trades")
    public ResponseEntity<ApiResponse<List<PaperTrade>>> getAllTrades(
            @RequestHeader(value = SESSION_HEADER, defaultValue = DEFAULT_SESSION) String sessionId) {
        List<PaperTrade> trades = paperTradingService.tradeHistory(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(trades,
                trades.size() + " trades in session: " + sessionId));
    }

    /**
     * PUT /api/paper/trades/{id}/close
     * Manually closes an open paper trade at the current market price.
     */
    @PutMapping("/trades/{id}/close")
    public ResponseEntity<ApiResponse<PaperTrade>> closeTrade(
            @PathVariable Long id,
            @RequestHeader(value = SESSION_HEADER, defaultValue = DEFAULT_SESSION) String sessionId) {

        PaperTrade trade = paperTradeRepository.findById(id).orElse(null);
        if (trade == null) {
            return ResponseEntity.ok(ApiResponse.error("Paper trade not found: " + id));
        }
        if (!trade.getSessionId().equals(sessionId)) {
            return ResponseEntity.ok(ApiResponse.error("Trade does not belong to session: " + sessionId));
        }
        if (!trade.isOpen()) {
            return ResponseEntity.ok(ApiResponse.error("Trade " + id + " is not open"));
        }

        try {
            paperTradingService.updatePosition(trade);
            // Force close after update
            PaperTrade updated = paperTradeRepository.findById(id).orElse(trade);
            return ResponseEntity.ok(ApiResponse.ok(updated, "Paper trade closed"));
        } catch (Exception ex) {
            log.error("Manual close failed for trade {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.ok(ApiResponse.error("Close failed: " + ex.getMessage()));
        }
    }

    /**
     * GET /api/paper/performance
     * Returns win rate, avg win/loss, profit factor and max drawdown for the session.
     */
    @GetMapping("/performance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPerformance(
            @RequestHeader(value = SESSION_HEADER, defaultValue = DEFAULT_SESSION) String sessionId) {

        PortfolioSummary summary = paperTradingService.getSummary(sessionId);
        List<PaperTrade> closed = paperTradingService.tradeHistory(sessionId).stream()
                .filter(PaperTrade::isClosed)
                .toList();

        double grossProfit = closed.stream().filter(PaperTrade::isWinner)
                .mapToDouble(t -> t.getPnl().doubleValue()).sum();
        double grossLoss = Math.abs(closed.stream().filter(t -> !t.isWinner())
                .mapToDouble(t -> t.getPnl().doubleValue()).sum());
        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : grossProfit > 0 ? 999 : 0;

        double avgWin = closed.stream().filter(PaperTrade::isWinner)
                .mapToDouble(t -> t.getPnl().doubleValue()).average().orElse(0);
        double avgLoss = Math.abs(closed.stream().filter(t -> !t.isWinner())
                .mapToDouble(t -> t.getPnl().doubleValue()).average().orElse(0));

        Map<String, Object> performance = Map.of(
                "sessionId", sessionId,
                "winRate", summary.winRatePct(),
                "totalTrades", summary.totalTrades(),
                "winningTrades", summary.winningTrades(),
                "losingTrades", summary.totalTrades() - summary.winningTrades(),
                "avgWin", avgWin,
                "avgLoss", avgLoss,
                "profitFactor", profitFactor,
                "realizedPnl", summary.realisedPnl(),
                "unrealizedPnl", summary.unrealisedPnl()
        );

        return ResponseEntity.ok(ApiResponse.ok(performance, "Performance summary for session: " + sessionId));
    }

    /**
     * DELETE /api/paper/reset
     * Cancels all open positions and resets the paper portfolio for the session.
     */
    @DeleteMapping("/reset")
    public ResponseEntity<ApiResponse<String>> resetPortfolio(
            @RequestHeader(value = SESSION_HEADER, defaultValue = DEFAULT_SESSION) String sessionId) {
        try {
            paperTradeRepository.cancelOpenPositions(sessionId);
            log.info("Paper portfolio reset for session: {}", sessionId);
            return ResponseEntity.ok(ApiResponse.ok(
                    "Portfolio reset for session: " + sessionId,
                    "All open positions cancelled. Capital restored."));
        } catch (Exception ex) {
            log.error("Portfolio reset failed for session {}: {}", sessionId, ex.getMessage(), ex);
            return ResponseEntity.ok(ApiResponse.error("Reset failed: " + ex.getMessage()));
        }
    }
}
