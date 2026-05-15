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
@Table(name = "technical_indicators", indexes = {
    @Index(name = "idx_tech_ind_instrument_ts", columnList = "instrument_id, timestamp", unique = true),
    @Index(name = "idx_tech_ind_timestamp", columnList = "timestamp")
})
public class TechnicalIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "rsi_14", precision = 8, scale = 4)
    private BigDecimal rsi;

    @Column(name = "macd", precision = 12, scale = 4)
    private BigDecimal macd;

    @Column(name = "macd_signal", precision = 12, scale = 4)
    private BigDecimal macdSignal;

    @Column(name = "macd_hist", precision = 12, scale = 4)
    private BigDecimal macdHist;

    @Column(name = "ema_20", precision = 18, scale = 4)
    private BigDecimal ema20;

    @Column(name = "ema_50", precision = 18, scale = 4)
    private BigDecimal ema50;

    @Column(name = "ema_200", precision = 18, scale = 4)
    private BigDecimal ema200;

    @Column(name = "vwap", precision = 18, scale = 4)
    private BigDecimal vwap;

    @Column(name = "bb_upper", precision = 18, scale = 4)
    private BigDecimal bbUpper;

    @Column(name = "bb_middle", precision = 18, scale = 4)
    private BigDecimal bbMiddle;

    @Column(name = "bb_lower", precision = 18, scale = 4)
    private BigDecimal bbLower;

    @Column(name = "volume_ratio", precision = 8, scale = 4)
    private BigDecimal volumeRatio;

    @Column(name = "adx", precision = 8, scale = 4)
    private BigDecimal adx;

    @Column(name = "atr", precision = 12, scale = 4)
    private BigDecimal atr;

    @Column(name = "stoch_k", precision = 8, scale = 4)
    private BigDecimal stochK;

    @Column(name = "stoch_d", precision = 8, scale = 4)
    private BigDecimal stochD;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean isRsiOversold() {
        return rsi != null && rsi.doubleValue() < 30;
    }

    public boolean isRsiOverbought() {
        return rsi != null && rsi.doubleValue() > 70;
    }

    public boolean isMacdBullishCrossover() {
        return macd != null && macdSignal != null && macd.compareTo(macdSignal) > 0;
    }

    public boolean isStrongTrend() {
        return adx != null && adx.doubleValue() > 25;
    }
}
