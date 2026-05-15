package com.nsemcx.trading.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "backtest_results", indexes = {
    @Index(name = "idx_backtest_results_instrument", columnList = "instrument_id"),
    @Index(name = "idx_backtest_results_status", columnList = "status"),
    @Index(name = "idx_backtest_results_created_at", columnList = "created_at"),
    @Index(name = "idx_backtest_results_strategy", columnList = "strategy_name")
})
public class BacktestResult {

    public enum BacktestStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "initial_capital", nullable = false, precision = 20, scale = 2)
    private BigDecimal initialCapital;

    @Column(name = "final_capital", precision = 20, scale = 2)
    private BigDecimal finalCapital;

    /** Total return percentage over the backtest period. */
    @Column(name = "total_return", precision = 12, scale = 4)
    private BigDecimal totalReturn;

    /** Annualized return percentage (CAGR). */
    @Column(name = "annualized_return", precision = 12, scale = 4)
    private BigDecimal annualizedReturn;

    /** Maximum drawdown percentage (negative value). */
    @Column(name = "max_drawdown", precision = 12, scale = 4)
    private BigDecimal maxDrawdown;

    /** Sharpe ratio (annualized, risk-free rate = 0 by default). */
    @Column(name = "sharpe_ratio", precision = 10, scale = 4)
    private BigDecimal sharpeRatio;

    /** Sortino ratio (annualized, uses downside deviation). */
    @Column(name = "sortino_ratio", precision = 10, scale = 4)
    private BigDecimal sortinoRatio;

    /** Win rate as a percentage (0-100). */
    @Column(name = "win_rate", precision = 8, scale = 4)
    private BigDecimal winRate;

    @Column(name = "total_trades")
    private Integer totalTrades;

    @Column(name = "winning_trades")
    private Integer winningTrades;

    @Column(name = "losing_trades")
    private Integer losingTrades;

    /** Average profit on winning trades (absolute). */
    @Column(name = "avg_win", precision = 16, scale = 4)
    private BigDecimal avgWin;

    /** Average loss on losing trades (absolute). */
    @Column(name = "avg_loss", precision = 16, scale = 4)
    private BigDecimal avgLoss;

    /** Profit factor = gross profit / gross loss. */
    @Column(name = "profit_factor", precision = 10, scale = 4)
    private BigDecimal profitFactor;

    @Column(name = "commission_paid", precision = 16, scale = 2)
    private BigDecimal commissionPaid;

    @Column(name = "slippage_cost", precision = 16, scale = 2)
    private BigDecimal slippageCost;

    /** JSON blob of strategy-specific parameters used in this run. */
    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BacktestStatus status = BacktestStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    // ── Derived helpers ──────────────────────────────────────────────────────

    public boolean isCompleted() {
        return status == BacktestStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == BacktestStatus.FAILED;
    }

    public double getProfitFactorAsDouble() {
        return profitFactor != null ? profitFactor.doubleValue() : 0.0;
    }
}
