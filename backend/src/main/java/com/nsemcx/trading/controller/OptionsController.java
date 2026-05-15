package com.nsemcx.trading.controller;

import com.nsemcx.trading.event.ApiResponse;
import com.nsemcx.trading.model.Instrument;
import com.nsemcx.trading.model.OptionChain;
import com.nsemcx.trading.repository.InstrumentRepository;
import com.nsemcx.trading.repository.OptionChainRepository;
import com.nsemcx.trading.service.OptionChainService;
import com.nsemcx.trading.service.OptionChainService.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for options chain data and analytics.
 * Base path: /api/options
 */
@Slf4j
@RestController
@RequestMapping("/api/options")
@RequiredArgsConstructor
public class OptionsController {

    private final OptionChainService optionChainService;
    private final OptionChainRepository optionChainRepository;
    private final InstrumentRepository instrumentRepository;

    /**
     * GET /api/options/{symbol}/chain?expiry=2024-01-25
     * Returns the option chain for the given symbol and expiry date.
     * If expiry is not provided, defaults to the nearest monthly expiry.
     */
    @GetMapping("/{symbol}/chain")
    public ResponseEntity<ApiResponse<List<OptionChain>>> getOptionChain(
            @PathVariable String symbol,
            @RequestParam(required = false) String expiry) {

        Instrument instrument = instrumentRepository.findBySymbol(symbol).orElse(null);
        if (instrument == null) {
            return ResponseEntity.ok(ApiResponse.error("Instrument not found: " + symbol));
        }

        LocalDate expiryDate = expiry != null ? LocalDate.parse(expiry) : findNearestExpiry();

        try {
            List<OptionChain> chain = optionChainRepository.findByInstrumentIdAndExpiry(
                    instrument.getId(), expiryDate);
            if (chain.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("No option chain data found for " + symbol
                        + " expiry " + expiryDate));
            }
            return ResponseEntity.ok(ApiResponse.ok(chain,
                    "Option chain for " + symbol + " expiry " + expiryDate
                    + " (" + chain.size() + " strikes)"));
        } catch (Exception ex) {
            log.error("Option chain fetch failed for {}: {}", symbol, ex.getMessage(), ex);
            return ResponseEntity.ok(ApiResponse.error("Option chain fetch failed: " + ex.getMessage()));
        }
    }

    /**
     * GET /api/options/{symbol}/pcr
     * Returns the Put-Call Ratio for the given symbol.
     */
    @GetMapping("/{symbol}/pcr")
    public ResponseEntity<ApiResponse<PCRAnalysis>> getPutCallRatio(@PathVariable String symbol) {
        Instrument instrument = instrumentRepository.findBySymbol(symbol).orElse(null);
        if (instrument == null) {
            return ResponseEntity.ok(ApiResponse.error("Instrument not found: " + symbol));
        }
        try {
            LocalDate expiry = findNearestExpiry();
            PCRAnalysis pcr = optionChainService.calculatePCR(instrument.getId(), expiry);
            return ResponseEntity.ok(ApiResponse.ok(pcr, "PCR analysis for " + symbol));
        } catch (Exception ex) {
            log.error("PCR calculation failed for {}: {}", symbol, ex.getMessage(), ex);
            return ResponseEntity.ok(ApiResponse.error("PCR calculation failed: " + ex.getMessage()));
        }
    }

    /**
     * GET /api/options/{symbol}/maxpain
     * Returns the max pain strike for the given symbol.
     * Max pain is the strike price at which option buyers lose the most.
     */
    @GetMapping("/{symbol}/maxpain")
    public ResponseEntity<ApiResponse<MaxPainAnalysis>> getMaxPain(@PathVariable String symbol) {
        Instrument instrument = instrumentRepository.findBySymbol(symbol).orElse(null);
        if (instrument == null) {
            return ResponseEntity.ok(ApiResponse.error("Instrument not found: " + symbol));
        }
        try {
            LocalDate expiry = findNearestExpiry();
            // Use a neutral current price; the service calculates from OI
            MaxPainAnalysis maxPain = optionChainService.calculateMaxPain(instrument.getId(), expiry, 0.0);
            return ResponseEntity.ok(ApiResponse.ok(maxPain, "Max pain analysis for " + symbol));
        } catch (Exception ex) {
            log.error("Max pain calculation failed for {}: {}", symbol, ex.getMessage(), ex);
            return ResponseEntity.ok(ApiResponse.error("Max pain calculation failed: " + ex.getMessage()));
        }
    }

    /**
     * GET /api/options/{symbol}/setup
     * Returns top 3 actionable options setups (CE buy / PE buy / spread) for the symbol.
     * Derived from OI, IV and PCR analysis.
     */
    @GetMapping("/{symbol}/setup")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getOptionsSetup(
            @PathVariable String symbol) {

        Instrument instrument = instrumentRepository.findBySymbol(symbol).orElse(null);
        if (instrument == null) {
            return ResponseEntity.ok(ApiResponse.error("Instrument not found: " + symbol));
        }
        try {
            LocalDate expiry = findNearestExpiry();
            PCRAnalysis pcr = optionChainService.calculatePCR(instrument.getId(), expiry);
            MaxPainAnalysis maxPain = optionChainService.calculateMaxPain(instrument.getId(), expiry, 0.0);
            SupportResistanceFromOI srLevels = optionChainService.identifySupportResistance(
                    instrument.getId(), expiry, maxPain.currentPrice());

            // Build top-3 setups based on market data
            String bias = pcr.isBullish() ? "BULLISH" : pcr.isBearish() ? "BEARISH" : "NEUTRAL";

            List<Map<String, Object>> setups = List.of(
                    Map.of(
                            "rank", 1,
                            "type", pcr.isBullish() ? "CALL_BUY" : "PUT_BUY",
                            "strike", pcr.isBullish() ? srLevels.strongestResistance() : srLevels.strongestSupport(),
                            "expiry", expiry.toString(),
                            "bias", bias,
                            "pcr", pcr.pcr(),
                            "rationale", pcr.interpretation()
                    ),
                    Map.of(
                            "rank", 2,
                            "type", "BULL_CALL_SPREAD",
                            "lowerStrike", srLevels.strongestSupport(),
                            "upperStrike", srLevels.strongestResistance(),
                            "expiry", expiry.toString(),
                            "maxPain", maxPain.maxPainStrike(),
                            "rationale", "Max pain at " + maxPain.maxPainStrike() + ", "
                                    + maxPain.direction() + " bias"
                    ),
                    Map.of(
                            "rank", 3,
                            "type", "STRADDLE",
                            "strike", maxPain.maxPainStrike(),
                            "expiry", expiry.toString(),
                            "rationale", "High IV — straddle near max pain for volatility play"
                    )
            );

            return ResponseEntity.ok(ApiResponse.ok(setups,
                    "Top 3 options setups for " + symbol));
        } catch (Exception ex) {
            log.error("Options setup generation failed for {}: {}", symbol, ex.getMessage(), ex);
            return ResponseEntity.ok(ApiResponse.error("Options setup generation failed: " + ex.getMessage()));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private LocalDate findNearestExpiry() {
        // NSE monthly expiry is typically the last Thursday of the month
        LocalDate today = LocalDate.now();
        LocalDate lastDay = today.withDayOfMonth(today.lengthOfMonth());
        while (lastDay.getDayOfWeek().getValue() != 4) { // 4 = Thursday
            lastDay = lastDay.minusDays(1);
        }
        if (lastDay.isBefore(today)) {
            // Past this month's expiry — move to next month
            LocalDate nextMonth = today.plusMonths(1);
            lastDay = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
            while (lastDay.getDayOfWeek().getValue() != 4) {
                lastDay = lastDay.minusDays(1);
            }
        }
        return lastDay;
    }
}
