package com.nsemcx.trading.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "paper_trades", indexes = {
    @Index(name = "idx_paper_trades_session", columnList = "session_id"),
    @Index(name = "idx_paper_trades_instrument", columnList = "instrument_id"),
    @Index(name = "idx_paper_trades_status", columnList = "status"),
    @Index(name = "idx_paper_trades_entry_time", columnList = "entry_time"),
    @Index(name = "idx_paper_trades_signal", columnList = "signal_id")
})
public class PaperTrade {

    public enum PaperTradeDirection {
        LONG, SHORT
    }

    public enum PaperTradeStatus {
        OPEN, CLOSED, CANCELLED
    }

    public enum PaperExitReason {
        TARGET_HIT, SL_HIT, SIGNAL, EXPIRED, MANUAL, RESET
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Logical grouping identifier for a paper-trading session (e.g. "default", UUID). */
    @Column(name = "session_id", nullable = false, length = 50)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signal_id")
    private TradeSignal signal;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private PaperTradeDirection direction;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "entry_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 18, scale = 4)
    private BigDecimal exitPrice;

    @CreationTimestamp
    @Column(name = "entry_time", nullable = false, updatable = false)
    private OffsetDateTime entryTime;

    @Column(name = "exit_time")
    private OffsetDateTime exitTime;

    /** Realised (closed) or unrealised (open) P&L. */
    @Column(name = "pnl", precision = 16, scale = 4)
    @Builder.Default
    private BigDecimal pnl = BigDecimal.ZERO;

    /** P&L as a percentage of capital deployed. */
    @Column(name = "pnl_percent", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal pnlPercent = BigDecimal.ZERO;

    /** Commission charged for this trade (simulated). */
    @Column(name = "commission", precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal commission = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PaperTradeStatus status = PaperTradeStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_reason")
    private PaperExitReason exitReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ── Derived helpers ──────────────────────────────────────────────────────

    public boolean isOpen() {
        return status == PaperTradeStatus.OPEN;
    }

    public boolean isClosed() {
        return status == PaperTradeStatus.CLOSED;
    }

    public boolean isLong() {
        return direction == PaperTradeDirection.LONG;
    }

    public boolean isWinner() {
        return pnl != null && pnl.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getCapitalDeployed() {
        return entryPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
