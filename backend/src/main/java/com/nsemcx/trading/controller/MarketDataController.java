package com.nsemcx.trading.controller;

import com.nsemcx.trading.domain.Exchange;
import com.nsemcx.trading.event.ApiResponse;
import com.nsemcx.trading.model.Instrument;
import com.nsemcx.trading.model.MarketData;
import com.nsemcx.trading.model.TechnicalIndicator;
import com.nsemcx.trading.repository.InstrumentRepository;
import com.nsemcx.trading.repository.MarketDataRepository;
import com.nsemcx.trading.repository.TechnicalIndicatorRepository;
import com.nsemcx.trading.service.YahooFinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST endpoints for market data access and refresh.
 * Base path: /api/market-data
 */
@Slf4j
@RestController
@RequestMapping("/api/market-data")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataRepository marketDataRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final InstrumentRepository instrumentRepository;
    private final YahooFinanceService yahooFinanceService;

    // ── Historical data ────────────────────────────────────────────────────────

    /**
     * GET /api/market-data/{symbol}/history?period=1y&interval=1d
     * Returns historical OHLCV data for the given instrument symbol.
     * period: 1d, 5d, 1mo, 3mo, 6mo, 1y, 2y, 5y (default 1y)
     * interval: currently only daily data is supported from DB
     */
    @GetMapping("/{symbol}/history")
    public ResponseEntity<ApiResponse<List<MarketData>>> getHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1y") String period,
            @RequestParam(defaultValue = "1d") String interval) {

        Instrument instrument = instrumentRepository.findBySymbol(symbol)
                .orElse(null);
        if (instrument == null) {
            return ResponseEntity.ok(ApiResponse.error("Instrument not found: " + symbol));
        }

        int days = parsePeriodToDays(period);
        OffsetDateTime since = OffsetDateTime.now().minusDays(days);
        OffsetDateTime now = OffsetDateTime.now();

        List<MarketData> data = marketDataRepository.findByInstrumentIdAndDateRange(
                instrument.getId(), since, now);

        return ResponseEntity.ok(ApiResponse.ok(data,
                "Historical data for " + symbol + " (" + data.size() + " bars)"));
    }

    /**
     * GET /api/market-data/{symbol}/latest
     * Returns the latest OHLCV candle for the given instrument.
     */
    @GetMapping("/{symbol}/latest")
    public ResponseEntity<ApiResponse<MarketData>> getLatest(@PathVariable String symbol) {
        Instrument instrument = instrumentRepository.findBySymbol(symbol).orElse(null);
        if (instrument == null) {
            return ResponseEntity.ok(ApiResponse.error("Instrument not found: " + symbol));
        }

        return marketDataRepository.findLatestByInstrumentId(instrument.getId())
                .map(data -> ResponseEntity.ok(ApiResponse.ok(data, "Latest data for " + symbol)))
                .orElse(ResponseEntity.ok(ApiResponse.error("No data found for " + symbol)));
    }

    /**
     * GET /api/market-data/{symbol}/technicals
     * Returns the latest computed technical indicators for the instrument.
     */
    @GetMapping("/{symbol}/technicals")
    public ResponseEntity<ApiResponse<TechnicalIndicator>> getTechnicals(@PathVariable String symbol) {
        Instrument instrument = instrumentRepository.findBySymbol(symbol).orElse(null);
        if (instrument == null) {
            return ResponseEntity.ok(ApiResponse.error("Instrument not found: " + symbol));
        }

        return technicalIndicatorRepository.findLatestByInstrumentId(instrument.getId())
                .map(ti -> ResponseEntity.ok(ApiResponse.ok(ti, "Technical indicators for " + symbol)))
                .orElse(ResponseEntity.ok(ApiResponse.error("No technicals found for " + symbol)));
    }

    // ── Refresh ────────────────────────────────────────────────────────────────

    /**
     * POST /api/market-data/refresh
     * Triggers a full data refresh for all active instruments from Yahoo Finance.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh() {
        log.info("Manual market data refresh triggered via API");
        try {
            yahooFinanceService.refreshAllInstruments();
            return ResponseEntity.ok(ApiResponse.ok("Refresh completed", "Market data refresh triggered successfully"));
        } catch (Exception ex) {
            log.error("Market data refresh failed: {}", ex.getMessage(), ex);
            return ResponseEntity.ok(ApiResponse.error("Refresh failed: " + ex.getMessage()));
        }
    }

    // ── Instrument discovery ────────────────────────────────────────────────────

    /**
     * GET /api/market-data/instruments
     * Returns all active instruments grouped by exchange and segment.
     */
    @GetMapping("/instruments")
    public ResponseEntity<ApiResponse<Map<String, Map<String, List<Instrument>>>>> getAllInstruments() {
        List<Instrument> instruments = instrumentRepository.findAllActiveOrderedByExchangeAndSegment();

        Map<String, Map<String, List<Instrument>>> grouped = instruments.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getExchange().name(),
                        Collectors.groupingBy(i -> i.getSegment().name())));

        return ResponseEntity.ok(ApiResponse.ok(grouped, instruments.size() + " active instruments"));
    }

    /**
     * GET /api/market-data/instruments/{exchange}
     * Returns active instruments for the given exchange (NSE / MCX / BSE).
     */
    @GetMapping("/instruments/{exchange}")
    public ResponseEntity<ApiResponse<List<Instrument>>> getInstrumentsByExchange(
            @PathVariable String exchange) {
        try {
            Exchange ex = Exchange.valueOf(exchange.toUpperCase());
            List<Instrument> instruments = instrumentRepository.findByExchangeAndIsActiveTrue(ex);
            return ResponseEntity.ok(ApiResponse.ok(instruments, instruments.size() + " instruments on " + exchange));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(ApiResponse.error("Unknown exchange: " + exchange
                    + ". Valid values: NSE, BSE, MCX, NCDEX, GLOBAL"));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private int parsePeriodToDays(String period) {
        return switch (period.toLowerCase()) {
            case "1d"  -> 1;
            case "5d"  -> 5;
            case "1mo" -> 30;
            case "3mo" -> 90;
            case "6mo" -> 180;
            case "2y"  -> 730;
            case "5y"  -> 1825;
            default    -> 365; // 1y
        };
    }
}
