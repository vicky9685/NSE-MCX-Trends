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
@Table(name = "market_data", indexes = {
    @Index(name = "idx_market_data_instrument_ts", columnList = "instrument_id, timestamp", unique = true),
    @Index(name = "idx_market_data_timestamp", columnList = "timestamp"),
    @Index(name = "idx_market_data_instrument", columnList = "instrument_id")
})
public class MarketData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "open", nullable = false, precision = 18, scale = 4)
    private BigDecimal open;

    @Column(name = "high", nullable = false, precision = 18, scale = 4)
    private BigDecimal high;

    @Column(name = "low", nullable = false, precision = 18, scale = 4)
    private BigDecimal low;

    @Column(name = "close", nullable = false, precision = 18, scale = 4)
    private BigDecimal close;

    @Column(name = "volume", nullable = false)
    @Builder.Default
    private Long volume = 0L;

    @Column(name = "open_interest")
    @Builder.Default
    private Long openInterest = 0L;

    @Column(name = "adj_close", precision = 18, scale = 4)
    private BigDecimal adjClose;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public double getRange() {
        return high.subtract(low).doubleValue();
    }

    public double getBodySize() {
        return Math.abs(close.subtract(open).doubleValue());
    }

    public boolean isBullish() {
        return close.compareTo(open) > 0;
    }

    public boolean isBearish() {
        return close.compareTo(open) < 0;
    }

    public double getMidpoint() {
        return high.add(low).doubleValue() / 2.0;
    }
}
