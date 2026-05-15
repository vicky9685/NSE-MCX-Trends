package com.nsemcx.trading.controller;

import com.nsemcx.trading.agent.OrchestratorAgent;
import com.nsemcx.trading.domain.Exchange;
import com.nsemcx.trading.domain.Segment;
import com.nsemcx.trading.domain.SignalStatus;
import com.nsemcx.trading.event.ApiResponse;
import com.nsemcx.trading.model.TradeSignal;
import com.nsemcx.trading.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for trade signals.
 * Base path: /api/signals
 */
@Slf4j
@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalController {

    private final TradeSignalRepository tradeSignalRepository;
    private final OrchestratorAgent orchestratorAgent;

    // ── Queries ────────────────────────────────────────────────────────────────

    /**
     * GET /api/signals
     * Returns active signals with optional filters.
     *
     * @param segment  optional: EQUITY / FUTURES / OPTIONS / COMMODITY / CURRENCY / INDEX
     * @param exchange optional: NSE / BSE / MCX / NCDEX / GLOBAL
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TradeSignal>>> getActiveSignals(
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String exchange) {

        List<TradeSignal> signals;

        if (segment != null && exchange != null) {
            try {
                Segment seg = Segment.valueOf(segment.toUpperCase());
                Exchange exch = Exchange.valueOf(exchange.toUpperCase());
                signals = tradeSignalRepository.findAll().stream()
                        .filter(s -> s.getStatus() == SignalStatus.ACTIVE
                                && s.getInstrument().getSegment() == seg
                                && s.getInstrument().getExchange() == exch)
                        .toList();
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.ok(ApiResponse.error("Invalid segment or exchange filter"));
            }
        } else if (segment != null) {
            try {
                Segment seg = Segment.valueOf(segment.toUpperCase());
                signals = tradeSignalRepository.findAll().stream()
                        .filter(s -> s.getStatus() == SignalStatus.ACTIVE
                                && s.getInstrument().getSegment() == seg)
                        .toList();
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.ok(ApiResponse.error("Invalid segment: " + segment));
            }
        } else if (exchange != null) {
            try {
                Exchange exch = Exchange.valueOf(exchange.toUpperCase());
                signals = tradeSignalRepository.findAll().stream()
                        .filter(s -> s.getStatus() == SignalStatus.ACTIVE
                                && s.getInstrument().getExchange() == exch)
                        .toList();
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.ok(ApiResponse.error("Invalid exchange: " + exchange));
            }
        } else {
            signals = tradeSignalRepository.findAllActiveOrderByConfidence();
        }

        return ResponseEntity.ok(ApiResponse.ok(signals, signals.size() + " active signals"));
    }

    /**
     * GET /api/signals/{id}
     * Returns a single signal by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TradeSignal>> getSignal(@PathVariable Long id) {
        return tradeSignalRepository.findById(id)
                .map(s -> ResponseEntity.ok(ApiResponse.ok(s, "Signal found")))
                .orElse(ResponseEntity.ok(ApiResponse.error("Signal not found: " + id)));
    }

    /**
     * POST /api/signals/generate
     * Triggers asynchronous multi-agent signal generation for all instruments.
     * Returns immediately with a confirmation message.
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<String>> generateSignals() {
        log.info("Signal generation triggered via API");
        generateSignalsAsync();
        return ResponseEntity.ok(ApiResponse.ok("Signal generation started",
                "Multi-agent signal generation is running asynchronously"));
    }

    @Async("tradingExecutor")
    void generateSignalsAsync() {
        try {
            List<TradeSignal> signals = orchestratorAgent.generateAllSignals();
            log.info("Signal generation complete: {} signals generated", signals.size());
        } catch (Exception ex) {
            log.error("Signal generation failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * GET /api/signals/history?instrumentId=&days=7
     * Returns historical signals for a given instrument over a time window.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<TradeSignal>>> getSignalHistory(
            @RequestParam(required = false) Long instrumentId,
            @RequestParam(defaultValue = "7") int days) {

        OffsetDateTime since = OffsetDateTime.now().minusDays(Math.min(days, 365));
        List<TradeSignal> signals;

        if (instrumentId != null) {
            signals = tradeSignalRepository.findLatestByInstrumentId(instrumentId, 200).stream()
                    .filter(s -> s.getCreatedAt().isAfter(since))
                    .toList();
        } else {
            signals = tradeSignalRepository.findSignalsCreatedAfter(since);
        }

        return ResponseEntity.ok(ApiResponse.ok(signals,
                signals.size() + " signals in last " + days + " days"));
    }

    /**
     * PUT /api/signals/{id}/status
     * Updates a signal's status.
     * Request body: { "status": "CANCELLED" }
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TradeSignal>> updateSignalStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String statusStr = body.get("status");
        if (statusStr == null) {
            return ResponseEntity.ok(ApiResponse.error("Missing 'status' field in request body"));
        }

        SignalStatus newStatus;
        try {
            newStatus = SignalStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(ApiResponse.error("Invalid status: " + statusStr));
        }

        return tradeSignalRepository.findById(id)
                .map(signal -> {
                    signal.setStatus(newStatus);
                    if (newStatus == SignalStatus.TRIGGERED) {
                        signal.setTriggeredAt(OffsetDateTime.now());
                    }
                    TradeSignal saved = tradeSignalRepository.save(signal);
                    return ResponseEntity.ok(ApiResponse.ok(saved, "Signal status updated to " + newStatus));
                })
                .orElse(ResponseEntity.ok(ApiResponse.error("Signal not found: " + id)));
    }
}
