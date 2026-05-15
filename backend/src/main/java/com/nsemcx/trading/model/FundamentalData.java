package com.nsemcx.trading.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "fundamental_data", indexes = {
    @Index(name = "idx_fund_data_instrument", columnList = "instrument_id", unique = true)
})
public class FundamentalData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false, unique = true)
    private Instrument instrument;

    @Column(name = "revenue_growth", precision = 10, scale = 4)
    private BigDecimal revenueGrowth;

    @Column(name = "profit_growth", precision = 10, scale = 4)
    private BigDecimal profitGrowth;

    @Column(name = "debt_equity", precision = 10, scale = 4)
    private BigDecimal debtEquity;

    @Column(name = "roe", precision = 10, scale = 4)
    private BigDecimal roe;

    @Column(name = "roce", precision = 10, scale = 4)
    private BigDecimal roce;

    @Column(name = "promoter_holding", precision = 10, scale = 4)
    private BigDecimal promoterHolding;

    @Column(name = "pe_ratio", precision = 12, scale = 4)
    private BigDecimal peRatio;

    @Column(name = "pb_ratio", precision = 10, scale = 4)
    private BigDecimal pbRatio;

    @Column(name = "ev_ebitda", precision = 10, scale = 4)
    private BigDecimal evEbitda;

    @Column(name = "fii_flow", precision = 18, scale = 2)
    private BigDecimal fiiFlow;

    @Column(name = "dii_flow", precision = 18, scale = 2)
    private BigDecimal diiFlow;

    @Column(name = "market_cap", precision = 20, scale = 2)
    private BigDecimal marketCap;

    @Column(name = "dividend_yield", precision = 8, scale = 4)
    private BigDecimal dividendYield;

    @Column(name = "eps", precision = 12, scale = 4)
    private BigDecimal eps;

    @Column(name = "book_value", precision = 12, scale = 4)
    private BigDecimal bookValue;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public boolean hasGoodRoe() {
        return roe != null && roe.doubleValue() > 15.0;
    }

    public boolean hasGoodRoce() {
        return roce != null && roce.doubleValue() > 15.0;
    }

    public boolean hasLowDebt() {
        return debtEquity != null && debtEquity.doubleValue() < 1.0;
    }

    public boolean hasHighPromoterHolding() {
        return promoterHolding != null && promoterHolding.doubleValue() > 50.0;
    }
}
