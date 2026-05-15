package com.nsemcx.trading.controller;

import com.nsemcx.trading.event.ApiResponse;
import com.nsemcx.trading.model.ForwardTestResult;
import com.nsemcx.trading.model.Instrument;
import com.nsemcx.trading.repository.InstrumentRepository;
import com.nsemcx.trading.service.ForwardTestingService;
import com.nsemcx.trading.service.ForwardTestingService.ForwardTestAccuracy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for forward-testing accuracy analysis.
 * Base path: /api/forward-test
 */
@Slf4j
@RestController
@RequestMapping("/api/forward-test")
@RequiredArgsConstructor
public class ForwardTestController {

    private final ForwardTestingService forwardTestingService;
    private final InstrumentRepository instrumentRepository;

    /**
     * GET /api/forward-test/accuracy
     * Returns a global accuracy report across all instruments.
     */
    @GetMapping("/accuracy")
    public ResponseEntity<ApiResponse<ForwardTestAccuracy>> getGlobalAccuracy() {
        ForwardTestAccuracy accuracy = forwardTestingService.getGlobalAccuracyReport();
        return ResponseEntity.ok(ApiResponse.ok(accuracy,
                "Global forward-test accuracy: " + String.format("%.1f%%", accuracy.overallAccuracyPct())));
    }

    /**
     * GET /api/forward-test/accuracy/{symbol}
     * Returns the accuracy report for a single instrument identified by its symbol.
     */
    @GetMapping("/accuracy/{symbol}")
    public ResponseEntity<ApiResponse<ForwardTestAccuracy>> getAccuracyBySymbol(
            @PathVariable String symbol) {

        Instrument instrument = instrumentRepository.findBySymbol(symbol).orElse(null);
        if (instrument == null) {
            return ResponseEntity.ok(ApiResponse.error("Instrument not found: " + symbol));
        }

        try {
            ForwardTestAccuracy accuracy = forwardTestingService.getAccuracyReport(instrument.getId());
            return ResponseEntity.ok(ApiResponse.ok(accuracy,
                    "Accuracy for " + symbol + ": " + String.format("%.1f%%", accuracy.overallAccuracyPct())
                    + " (" + accuracy.grade() + ")"));
        } catch (Exception ex) {
            log.error("Accuracy report failed for {}: {}", symbol, ex.getMessage(), ex);
            return ResponseEntity.ok(ApiResponse.error("Accuracy report failed: " + ex.getMessage()));
        }
    }

    /**
     * GET /api/forward-test/predictions?limit=50
     * Returns the most recent forward-test predictions (both evaluated and pending).
     */
    @GetMapping("/predictions")
    public ResponseEntity<ApiResponse<List<ForwardTestResult>>> getRecentPredictions(
            @RequestParam(defaultValue = "50") int limit) {

        List<ForwardTestResult> predictions = forwardTestingService.getRecentPredictions(limit);
        long evaluated = predictions.stream().filter(p -> p.getWasCorrect() != null).count();
        long correct = predictions.stream().filter(p -> Boolean.TRUE.equals(p.getWasCorrect())).count();

        String msg = predictions.size() + " predictions ("
                + evaluated + " evaluated, " + correct + " correct"
                + (evaluated > 0 ? ", " + String.format("%.1f%%", (double) correct / evaluated * 100) + " accuracy)" : ")");

        return ResponseEntity.ok(ApiResponse.ok(predictions, msg));
    }
}
