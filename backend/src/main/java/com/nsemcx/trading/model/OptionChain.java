package com.nsemcx.trading.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "option_chain", indexes = {
    @Index(name = "idx_option_chain_instrument", columnList = "instrument_id"),
    @Index(name = "idx_option_chain_expiry", columnList = "expiry"),
    @Index(name = "idx_option_chain_timestamp", columnList = "timestamp"),
    @Index(name = "idx_option_chain_unique", columnList = "instrument_id, expiry, strike, timestamp", unique = true)
})
public class OptionChain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "expiry", nullable = false)
    private LocalDate expiry;

    @Column(name = "strike", nullable = false, precision = 18, scale = 2)
    private BigDecimal strike;

    @Column(name = "ce_oi")
    @Builder.Default
    private Long ceOi = 0L;

    @Column(name = "pe_oi")
    @Builder.Default
    private Long peOi = 0L;

    @Column(name = "ce_change_oi")
    @Builder.Default
    private Long ceChangeOi = 0L;

    @Column(name = "pe_change_oi")
    @Builder.Default
    private Long peChangeOi = 0L;

    @Column(name = "ce_iv", precision = 8, scale = 4)
    private BigDecimal ceIv;

    @Column(name = "pe_iv", precision = 8, scale = 4)
    private BigDecimal peIv;

    @Column(name = "ce_ltp", precision = 12, scale = 4)
    private BigDecimal ceLtp;

    @Column(name = "pe_ltp", precision = 12, scale = 4)
    private BigDecimal peLtp;

    @Column(name = "ce_volume")
    @Builder.Default
    private Long ceVolume = 0L;

    @Column(name = "pe_volume")
    @Builder.Default
    private Long peVolume = 0L;

    @Column(name = "pcr", precision = 8, scale = 4)
    private BigDecimal pcr;

    @Column(name = "max_pain", precision = 18, scale = 2)
    private BigDecimal maxPain;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public double getPcrValue() {
        if (ceOi == null || ceOi == 0) return 0;
        return (double) (peOi != null ? peOi : 0) / ceOi;
    }
}
