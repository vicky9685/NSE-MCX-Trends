package com.nsemcx.trading.model;

import com.nsemcx.trading.domain.SignalStatus;
import com.nsemcx.trading.domain.SignalType;
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
@Table(name = "trade_signals", indexes = {
    @Index(name = "idx_signals_instrument", columnList = "instrument_id"),
    @Index(name = "idx_signals_status", columnList = "status"),
    @Index(name = "idx_signals_created_at", columnList = "created_at"),
    @Index(name = "idx_signals_type", columnList = "signal_type")
})
public class TradeSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false)
    private SignalType signalType;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "entry_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "target1", precision = 18, scale = 4)
    private BigDecimal target1;

    @Column(name = "target2", precision = 18, scale = 4)
    private BigDecimal target2;

    @Column(name = "target3", precision = 18, scale = 4)
    private BigDecimal target3;

    @Column(name = "stoploss", precision = 18, scale = 4)
    private BigDecimal stoploss;

    @Column(name = "risk_reward", precision = 8, scale = 4)
    private BigDecimal riskReward;

    @Column(name = "time_horizon", length = 50)
    private String timeHorizon;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "hedging_strategy", columnDefinition = "TEXT")
    private String hedgingStrategy;

    @Column(name = "strike_price", precision = 18, scale = 4)
    private BigDecimal strikePrice;

    @Column(name = "probability", precision = 5, scale = 2)
    private BigDecimal probability;

    @Column(name = "fundamental_score", precision = 5, scale = 2)
    private BigDecimal fundamentalScore;

    @Column(name = "technical_score", precision = 5, scale = 2)
    private BigDecimal technicalScore;

    @Column(name = "macro_score", precision = 5, scale = 2)
    private BigDecimal macroScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "triggered_at")
    private OffsetDateTime triggeredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SignalStatus status = SignalStatus.ACTIVE;

    public double getCompositeScore() {
        double fundScore = fundamentalScore != null ? fundamentalScore.doubleValue() : 0;
        double techScore = technicalScore != null ? technicalScore.doubleValue() : 0;
        double macroScoreVal = macroScore != null ? macroScore.doubleValue() : 0;
        return (fundScore * 0.60) + (macroScoreVal * 0.20) + (techScore * 0.20);
    }

    public boolean isActive() {
        return status == SignalStatus.ACTIVE;
    }
}
