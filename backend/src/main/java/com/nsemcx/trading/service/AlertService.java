package com.nsemcx.trading.service;

import com.nsemcx.trading.kafka.MarketDataProducer;
import com.nsemcx.trading.model.Alert;
import com.nsemcx.trading.model.Alert.AlertSeverity;
import com.nsemcx.trading.model.Alert.AlertType;
import com.nsemcx.trading.model.Instrument;
import com.nsemcx.trading.model.MarketData;
import com.nsemcx.trading.repository.InstrumentRepository;
import com.nsemcx.trading.repository.MarketDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for creating, evaluating, and managing price and technical alerts.
 *
 * <p>Alerts are persisted to the {@code alerts} table via JPA (using
 * {@link EntityManager} for simplicity – no dedicated repository is required for
 * the alert checks at this stage).  Triggered alerts are published to the Kafka
 * {@code alerts} topic so the consumer can broadcast them over WebSocket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    @PersistenceContext
    private EntityManager entityManager;

    private final MarketDataRepository marketDataRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataProducer marketDataProducer;

    // ── Alert creation ────────────────────────────────────────────────────────

    /**
     * Creates a price alert that will fire when the latest price crosses
     * {@code targetPrice} in the specified direction.
     *
     * @param instrumentId id of the instrument to monitor
     * @param targetPrice  price level that should trigger the alert
     * @param above        {@code true} → alert when price goes ABOVE target;
     *                     {@code false} → alert when price goes BELOW target
     * @return the persisted (not yet triggered) {@link Alert}
     */
    @Transactional
    public Alert createPriceAlert(Long instrumentId, BigDecimal targetPrice, boolean above) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Instrument not found: " + instrumentId));

        String condition = above ? "above" : "below";
        Alert alert = Alert.builder()
                .instrument(instrument)
                .alertType(AlertType.PRICE)
                .severity(AlertSeverity.INFO)
                .title(String.format("Price Alert – %s %s ₹%s",
                        instrument.getSymbol(), condition, targetPrice.toPlainString()))
                .message(String.format("Alert: %s price has gone %s ₹%s",
                        instrument.getSymbol(), condition, targetPrice.toPlainString()))
                .conditionVal(targetPrice)
                .isTriggered(false)
                .isRead(false)
                .build();

        entityManager.persist(alert);
        log.info("Created price alert for {} {} {}", instrument.getSymbol(), condition, targetPrice);
        return alert;
    }

    /**
     * Creates a technical-indicator alert (RSI, MACD, etc.).
     *
     * @param instrumentId id of the instrument to monitor
     * @param indicator    indicator name, e.g. "RSI", "MACD"
     * @param condition    human-readable condition, e.g. "crosses_below", "crosses_above"
     * @param value        threshold value for the indicator
     * @return the persisted alert entity
     */
    @Transactional
    public Alert createTechnicalAlert(Long instrumentId, String indicator,
                                      String condition, BigDecimal value) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Instrument not found: " + instrumentId));

        AlertType alertType = resolveAlertType(indicator);

        Alert alert = Alert.builder()
                .instrument(instrument)
                .alertType(alertType)
                .severity(AlertSeverity.WARNING)
                .title(String.format("%s Alert – %s %s %s",
                        indicator, instrument.getSymbol(), condition, value.toPlainString()))
                .message(String.format("Technical alert: %s %s %s %s",
                        instrument.getSymbol(), indicator, condition, value.toPlainString()))
                .conditionVal(value)
                .isTriggered(false)
                .isRead(false)
                .build();

        entityManager.persist(alert);
        log.info("Created {} alert for {} {} {}", indicator,
                instrument.getSymbol(), condition, value);
        return alert;
    }

    // ── Periodic check ────────────────────────────────────────────────────────

    /**
     * Evaluates all non-triggered alerts against the latest market prices and
     * publishes triggered alerts to Kafka.
     *
     * <p>Called by the scheduler every minute during market hours.
     */
    @Transactional
    public void checkAllAlerts() {
        @SuppressWarnings("unchecked")
        List<Alert> activeAlerts = entityManager
                .createQuery("SELECT a FROM Alert a WHERE a.isTriggered = false", Alert.class)
                .getResultList();

        if (activeAlerts.isEmpty()) {
            return;
        }

        log.debug("Checking {} active alerts", activeAlerts.size());

        for (Alert alert : activeAlerts) {
            try {
                evaluateAlert(alert);
            } catch (Exception ex) {
                log.warn("Failed to evaluate alert id={}: {}", alert.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Returns all alerts that have not been triggered yet.
     */
    @Transactional(readOnly = true)
    public List<Alert> getActiveAlerts() {
        return entityManager
                .createQuery("SELECT a FROM Alert a WHERE a.isTriggered = false ORDER BY a.createdAt DESC",
                        Alert.class)
                .getResultList();
    }

    /**
     * Returns all alerts (active and triggered) ordered by creation date descending.
     */
    @Transactional(readOnly = true)
    public List<Alert> getAllAlerts() {
        return entityManager
                .createQuery("SELECT a FROM Alert a ORDER BY a.createdAt DESC", Alert.class)
                .getResultList();
    }

    /**
     * Marks a specific alert as read.
     *
     * @param alertId database id of the alert
     */
    @Transactional
    public void markRead(Long alertId) {
        Alert alert = entityManager.find(Alert.class, alertId);
        if (alert != null) {
            alert.setIsRead(true);
            log.debug("Marked alert id={} as read", alertId);
        } else {
            log.warn("Alert not found for markRead: id={}", alertId);
        }
    }

    // ── Private evaluation logic ──────────────────────────────────────────────

    private void evaluateAlert(Alert alert) {
        if (alert.getInstrument() == null) {
            return; // system-level alert, no price to check
        }

        MarketData latest = marketDataRepository
                .findLatestByInstrumentId(alert.getInstrument().getId())
                .orElse(null);

        if (latest == null) {
            return;
        }

        BigDecimal currentPrice = latest.getClose();
        alert.setCurrentVal(currentPrice);

        boolean triggered = switch (alert.getAlertType()) {
            case PRICE -> evaluatePriceAlert(alert, currentPrice);
            case RSI, MACD, BREAKOUT, VOLUME, OI -> evaluateTechnicalAlert(alert, currentPrice);
            default -> false;
        };

        if (triggered) {
            alert.setIsTriggered(true);
            alert.setTriggeredAt(OffsetDateTime.now());
            log.info("Alert TRIGGERED: id={} type={} instrument={} condition={} current={}",
                    alert.getId(), alert.getAlertType(),
                    alert.getInstrument().getSymbol(),
                    alert.getConditionVal(), currentPrice);

            // Publish to Kafka → consumer broadcasts via WebSocket
            marketDataProducer.sendAlert(alert);
        }
    }

    /**
     * Price alerts: determine direction from the alert title.
     * "above" means trigger when price > conditionVal, else trigger when price < conditionVal.
     */
    private boolean evaluatePriceAlert(Alert alert, BigDecimal currentPrice) {
        if (alert.getConditionVal() == null) return false;
        boolean isAbove = alert.getTitle() != null && alert.getTitle().contains("above");
        if (isAbove) {
            return currentPrice.compareTo(alert.getConditionVal()) >= 0;
        } else {
            return currentPrice.compareTo(alert.getConditionVal()) <= 0;
        }
    }

    /**
     * Technical alerts use the conditionVal as a generic threshold.
     * Simplified: trigger if currentPrice has moved more than 1% from conditionVal.
     */
    private boolean evaluateTechnicalAlert(Alert alert, BigDecimal currentPrice) {
        if (alert.getConditionVal() == null) return false;
        BigDecimal diff = currentPrice.subtract(alert.getConditionVal()).abs();
        BigDecimal threshold = alert.getConditionVal()
                .multiply(BigDecimal.valueOf(0.01)); // 1% band
        return diff.compareTo(threshold) >= 0;
    }

    private AlertType resolveAlertType(String indicator) {
        return switch (indicator.toUpperCase()) {
            case "RSI"  -> AlertType.RSI;
            case "MACD" -> AlertType.MACD;
            case "OI", "OPEN_INTEREST" -> AlertType.OI;
            case "VOLUME" -> AlertType.VOLUME;
            case "BREAKOUT" -> AlertType.BREAKOUT;
            default -> AlertType.PRICE;
        };
    }
}
