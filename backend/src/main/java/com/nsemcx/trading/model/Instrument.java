package com.nsemcx.trading.model;

import com.nsemcx.trading.domain.Exchange;
import com.nsemcx.trading.domain.Segment;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "instruments", indexes = {
    @Index(name = "idx_instruments_symbol", columnList = "symbol"),
    @Index(name = "idx_instruments_exchange", columnList = "exchange"),
    @Index(name = "idx_instruments_segment", columnList = "segment")
})
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, unique = true, length = 50)
    private String symbol;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "segment", nullable = false)
    private Segment segment;

    @Enumerated(EnumType.STRING)
    @Column(name = "exchange", nullable = false)
    private Exchange exchange;

    @Column(name = "lot_size", nullable = false)
    @Builder.Default
    private Integer lotSize = 1;

    @Column(name = "tick_size", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal tickSize = BigDecimal.valueOf(0.05);

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "isin", length = 20)
    private String isin;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public String getYahooSymbol() {
        return switch (exchange) {
            case NSE -> symbol.endsWith(".NS") ? symbol : symbol + ".NS";
            case BSE -> symbol.endsWith(".BO") ? symbol : symbol + ".BO";
            case MCX -> switch (symbol.toUpperCase()) {
                case "GOLD" -> "GC=F";
                case "SILVER" -> "SI=F";
                case "CRUDEOIL" -> "CL=F";
                case "NATURALGAS" -> "NG=F";
                case "COPPER" -> "HG=F";
                default -> symbol;
            };
            default -> symbol;
        };
    }
}
