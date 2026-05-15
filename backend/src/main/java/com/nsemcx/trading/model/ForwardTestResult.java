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
@Table(name = "forward_test_results", indexes = {
    @Index(name = "idx_fwd_test_signal", columnList = "signal_id"),
    @Index(name = "idx_fwd_test_instrument", columnList = "instrument_id"),
    @Index(name = "idx_fwd_test_prediction_date", columnList = "prediction_date"),
    @Index(name = "idx_fwd_test_was_correct", columnList = "was_correct"),
    @Index(name = "idx_fwd_test_eval_pending", columnList = "prediction_date, was_correct")
})
public class ForwardTestResult {

    public enum PredictionDirection {
        BULLISH, BEARISH, NEUTRAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signal_id", nullable = false)
    private TradeSignal signal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    /** Date on which the prediction was recorded. */
    @Column(name = "prediction_date", nullable = false)
    private LocalDate predictionDate;

    /** Number of trading days to look ahead for evaluation. */
    @Column(name = "look_ahead_days", nullable = false)
    private Integer lookAheadDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "predicted_direction", nullable = false)
    private PredictionDirection predictedDirection;

    @Enumerated(EnumType.STRING)
    @Column(name = "actual_direction")
    private PredictionDirection actualDirection;

    /** Price target predicted by the signal. */
    @Column(name = "predicted_target", precision = 18, scale = 4)
    private BigDecimal predictedTarget;

    /** Actual market price at predictionDate + lookAheadDays. Null until evaluated. */
    @Column(name = "actual_price", precision = 18, scale = 4)
    private BigDecimal actualPrice;

    /**
     * Null = not yet evaluated (evaluation date in the future).
     * true  = prediction matched actual direction.
     * false = prediction was wrong.
     */
    @Column(name = "was_correct")
    private Boolean wasCorrect;

    /** Model confidence score at time of prediction (0-100). */
    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ── Derived helpers ──────────────────────────────────────────────────────

    public boolean isPendingEvaluation() {
        return wasCorrect == null;
    }

    public LocalDate getEvaluationDate() {
        return predictionDate.plusDays(lookAheadDays);
    }

    public boolean isBullishPrediction() {
        return predictedDirection == PredictionDirection.BULLISH;
    }

    public boolean isBearishPrediction() {
        return predictedDirection == PredictionDirection.BEARISH;
    }
}
