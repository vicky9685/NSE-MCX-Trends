package com.nsemcx.trading.model;

import com.nsemcx.trading.domain.SignalType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "backtest_trades", indexes = {
    @Index(name = "idx_bt_trades_result", columnList = "backtest_result_id"),
    @Index(name = "idx_bt_trades_instrument", columnList = "instrument_id"),
    @Index(name = "idx_bt_trades_entry_date", columnList = "entry_date"),
    @Index(name = "idx_bt_trades_direction", columnList = "direction")
})
public class BacktestTrade {

    public enum TradeDirection {
        LONG, SHORT
    }

    public enum ExitReason {
        TARGET_HIT, SL_HIT, SIGNAL, EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backtest_result_id", nullable = false)
    private BacktestResult backtestResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "entry_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 18, scale = 4)
    private BigDecimal exitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private TradeDirection direction;

    /** Net P&L after commission and slippage. */
    @Column(name = "pnl", precision = 16, scale = 4)
    private BigDecimal pnl;

    /** P&L as a percentage of invested capital. */
    @Column(name = "pnl_percent", precision = 10, scale = 4)
    private BigDecimal pnlPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_reason")
    private ExitReason exitReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type")
    private SignalType signalType;

    /** Number of calendar days the position was held. */
    @Column(name = "holding_days")
    private Integer holdingDays;

    // ── Derived helpers ──────────────────────────────────────────────────────

    public boolean isWinner() {
        return pnl != null && pnl.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isLoser() {
        return pnl != null && pnl.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isLong() {
        return direction == TradeDirection.LONG;
    }

    public boolean isShort() {
        return direction == TradeDirection.SHORT;
    }
}
