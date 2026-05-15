package com.nsemcx.trading.controller;

import com.nsemcx.trading.event.ApiResponse;
import com.nsemcx.trading.model.BacktestResult;
import com.nsemcx.trading.model.BacktestTrade;
import com.nsemcx.trading.repository.BacktestResultRepository;
import com.nsemcx.trading.repository.BacktestTradeRepository;
import com.nsemcx.trading.service.BacktestingService;
import com.nsemcx.trading.service.BacktestingService.BacktestRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for backtesting.
 * Base path: /api/backtest
 */
@Slf4j
@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestingService backtestingService;
    private final BacktestResultRepository backtestResultRepository;
    private final BacktestTradeRepository backtestTradeRepository;

    // ── Request DTOs ──────────────────────────────────────────────────────────

    /**
     * Request body for POST /api/backtest/run
     */
    public record BacktestRunRequest(
            @NotNull Long instrumentId,
            @NotNull String strategyName,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            BigDecimal initialCapital,
            String parametersJson,
            boolean async
    ) {}

    /**
     * Request body for POST /api/backtest/compare
     */
    public record CompareRequest(
            @NotNull List<String> strategyNames,
            @NotNull Long instrumentId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate
    ) {}

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * POST /api/backtest/run
     * Runs a backtest. If async=true, returns the backtestId immediately;
     * progress is pushed to WebSocket /topic/backtest/{id}.
     * If async=false (default), blocks until complete and returns the full result.
     */
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<Object>> runBacktest(
            @Valid @RequestBody BacktestRunRequest req) {

        BacktestRequest request = new BacktestRequest(
                req.instrumentId(),
                req.strategyName(),
                req.startDate(),
                req.endDate(),
                req.initialCapital(),
                req.parametersJson()
        );

        if (req.async()) {
            backtestingService.runBacktestAsync(request);
            return ResponseEntity.ok(ApiResponse.ok(
                    Map.of("message", "Backtest started asynchronously",
                           "strategy", req.strategyName(),
                           "instrumentId", req.instrumentId()),
                    "Backtest submitted. Monitor progress on WebSocket /topic/backtest/{id}"));
        }

        try {
            BacktestResult result = backtestingService.runBacktest(request);
            return ResponseEntity.ok(ApiResponse.ok(result,
                    "Backtest completed: " + result.getTotalTrades() + " trades"));
        } catch (Exception ex) {
            log.error("Backtest run failed: {}", ex.getMessage(), ex);
            return ResponseEntity.ok(ApiResponse.error("Backtest failed: " + ex.getMessage()));
        }
    }

    /**
     * GET /api/backtest/results
     * Returns all backtest results, optionally filtered by instrument.
     */
    @GetMapping("/results")
    public ResponseEntity<ApiResponse<List<BacktestResult>>> getAllResults(
            @RequestParam(required = false) Long instrumentId) {
        List<BacktestResult> results = backtestingService.getBacktestResults(instrumentId);
        return ResponseEntity.ok(ApiResponse.ok(results, results.size() + " backtest results"));
    }

    /**
     * GET /api/backtest/results/{id}
     * Returns a single backtest result with summary metrics.
     */
    @GetMapping("/results/{id}")
    public ResponseEntity<ApiResponse<BacktestResult>> getResult(@PathVariable Long id) {
        return backtestResultRepository.findById(id)
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r, "Backtest result " + id)))
                .orElse(ResponseEntity.ok(ApiResponse.error("Backtest result not found: " + id)));
    }

    /**
     * GET /api/backtest/results/{id}/trades?page=0&size=50
     * Returns a paginated list of individual trades for the given backtest.
     */
    @GetMapping("/results/{id}/trades")
    public ResponseEntity<ApiResponse<Page<BacktestTrade>>> getResultTrades(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        if (!backtestResultRepository.existsById(id)) {
            return ResponseEntity.ok(ApiResponse.error("Backtest result not found: " + id));
        }

        PageRequest pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.ASC, "entryDate"));
        Page<BacktestTrade> trades = backtestTradeRepository.findByBacktestResultId(id, pageable);
        return ResponseEntity.ok(ApiResponse.ok(trades,
                trades.getTotalElements() + " trades (page " + page + ")"));
    }

    /**
     * GET /api/backtest/strategies
     * Returns the list of available strategy names that can be used in backtest runs.
     */
    @GetMapping("/strategies")
    public ResponseEntity<ApiResponse<List<String>>> getStrategies() {
        List<String> strategies = backtestingService.availableStrategyNames();
        return ResponseEntity.ok(ApiResponse.ok(strategies, strategies.size() + " strategies available"));
    }

    /**
     * POST /api/backtest/compare
     * Runs the same date range and instrument through multiple strategies
     * and returns comparative results.
     */
    @PostMapping("/compare")
    public ResponseEntity<ApiResponse<Map<String, BacktestResult>>> compareStrategies(
            @Valid @RequestBody CompareRequest req) {

        if (req.strategyNames().isEmpty() || req.strategyNames().size() > 10) {
            return ResponseEntity.ok(ApiResponse.error(
                    "Please provide 1-10 strategy names for comparison"));
        }

        try {
            Map<String, BacktestResult> results = backtestingService.compareStrategies(
                    req.strategyNames(), req.instrumentId(), req.startDate(), req.endDate());
            return ResponseEntity.ok(ApiResponse.ok(results,
                    "Compared " + results.size() + " strategies"));
        } catch (Exception ex) {
            log.error("Strategy comparison failed: {}", ex.getMessage(), ex);
            return ResponseEntity.ok(ApiResponse.error("Comparison failed: " + ex.getMessage()));
        }
    }
}
