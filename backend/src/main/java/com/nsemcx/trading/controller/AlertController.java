package com.nsemcx.trading.controller;

import com.nsemcx.trading.event.ApiResponse;
import com.nsemcx.trading.model.Alert;
import com.nsemcx.trading.model.Alert.AlertSeverity;
import com.nsemcx.trading.model.Alert.AlertType;
import com.nsemcx.trading.model.Instrument;
import com.nsemcx.trading.repository.AlertRepository;
import com.nsemcx.trading.repository.InstrumentRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for price and technical alerts.
 * Base path: /api/alerts
 */
@Slf4j
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRepository alertRepository;
    private final InstrumentRepository instrumentRepository;

    // ── Request DTO ───────────────────────────────────────────────────────────

    public record CreateAlertRequest(
            @NotNull Long instrumentId,
            @NotNull AlertType alertType,
            @NotBlank String title,
            @NotBlank String message,
            AlertSeverity severity,
            BigDecimal conditionVal
    ) {}

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * GET /api/alerts
     * Returns all alerts with optional filters:
     *   ?triggered=true/false — filter by trigger status
     *   ?severity=CRITICAL    — filter by severity level
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Alert>>> getAlerts(
            @RequestParam(required = false) Boolean triggered,
            @RequestParam(required = false) String severity) {

        List<Alert> alerts;

        if (triggered != null && severity != null) {
            try {
                AlertSeverity sev = AlertSeverity.valueOf(severity.toUpperCase());
                alerts = alertRepository.findByIsTriggeredAndSeverityOrderByCreatedAtDesc(triggered, sev);
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.ok(ApiResponse.error("Invalid severity: " + severity));
            }
        } else if (triggered != null) {
            alerts = alertRepository.findByIsTriggeredOrderByCreatedAtDesc(triggered);
        } else if (severity != null) {
            try {
                AlertSeverity sev = AlertSeverity.valueOf(severity.toUpperCase());
                alerts = alertRepository.findBySeverityOrderByCreatedAtDesc(sev);
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.ok(ApiResponse.error("Invalid severity: " + severity));
            }
        } else {
            alerts = alertRepository.findAll().stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .toList();
        }

        return ResponseEntity.ok(ApiResponse.ok(alerts, alerts.size() + " alerts"));
    }

    /**
     * POST /api/alerts
     * Creates a new price or technical alert for an instrument.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Alert>> createAlert(
            @RequestBody CreateAlertRequest req) {

        Instrument instrument = instrumentRepository.findById(req.instrumentId()).orElse(null);
        if (instrument == null) {
            return ResponseEntity.ok(ApiResponse.error("Instrument not found: " + req.instrumentId()));
        }

        Alert alert = Alert.builder()
                .instrument(instrument)
                .alertType(req.alertType())
                .title(req.title())
                .message(req.message())
                .severity(req.severity() != null ? req.severity() : AlertSeverity.INFO)
                .conditionVal(req.conditionVal())
                .isTriggered(false)
                .isRead(false)
                .build();

        Alert saved = alertRepository.save(alert);
        log.info("Alert created: id={}, type={}, instrument={}", saved.getId(), saved.getAlertType(), instrument.getSymbol());
        return ResponseEntity.ok(ApiResponse.ok(saved, "Alert created for " + instrument.getSymbol()));
    }

    /**
     * PUT /api/alerts/{id}/read
     * Marks a single alert as read.
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<String>> markAsRead(@PathVariable Long id) {
        if (!alertRepository.existsById(id)) {
            return ResponseEntity.ok(ApiResponse.error("Alert not found: " + id));
        }
        int updated = alertRepository.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.ok(
                updated > 0 ? "Alert " + id + " marked as read" : "Alert already read",
                "OK"));
    }

    /**
     * DELETE /api/alerts/{id}
     * Deletes an alert permanently.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteAlert(@PathVariable Long id) {
        if (!alertRepository.existsById(id)) {
            return ResponseEntity.ok(ApiResponse.error("Alert not found: " + id));
        }
        alertRepository.deleteById(id);
        log.info("Alert {} deleted", id);
        return ResponseEntity.ok(ApiResponse.ok("Alert " + id + " deleted", "OK"));
    }

    /**
     * GET /api/alerts/unread/count
     * Returns the count of unread alerts (both triggered and not-yet-triggered).
     */
    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount() {
        long total = alertRepository.countUnread();
        long triggered = alertRepository.countUnreadTriggered();
        long critical = alertRepository.countUnreadBySeverity(AlertSeverity.CRITICAL);

        Map<String, Long> counts = Map.of(
                "total", total,
                "triggered", triggered,
                "critical", critical
        );
        return ResponseEntity.ok(ApiResponse.ok(counts, total + " unread alerts"));
    }
}
