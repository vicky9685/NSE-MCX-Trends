package com.nsemcx.trading.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alerts_instrument", columnList = "instrument_id"),
    @Index(name = "idx_alerts_is_triggered", columnList = "is_triggered"),
    @Index(name = "idx_alerts_created_at", columnList = "created_at"),
    @Index(name = "idx_alerts_severity", columnList = "severity")
})
public class Alert {

    public enum AlertType {
        PRICE, VOLUME, RSI, MACD, BREAKOUT, SIGNAL, RISK, OI
    }

    public enum AlertSeverity {
        INFO, WARNING, CRITICAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signal_id")
    private TradeSignal signal;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    @Builder.Default
    private AlertSeverity severity = AlertSeverity.INFO;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "condition_val", precision = 18, scale = 4)
    private BigDecimal conditionVal;

    @Column(name = "current_val", precision = 18, scale = 4)
    private BigDecimal currentVal;

    @Column(name = "is_triggered", nullable = false)
    @Builder.Default
    private Boolean isTriggered = false;

    @Column(name = "triggered_at")
    private OffsetDateTime triggeredAt;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
