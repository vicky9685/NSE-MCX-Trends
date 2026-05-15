package com.nsemcx.trading.repository;

import com.nsemcx.trading.model.ForwardTestResult;
import com.nsemcx.trading.model.ForwardTestResult.PredictionDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ForwardTestRepository extends JpaRepository<ForwardTestResult, Long> {

    List<ForwardTestResult> findByInstrumentIdOrderByPredictionDateDesc(Long instrumentId);

    List<ForwardTestResult> findBySignalIdOrderByCreatedAtDesc(Long signalId);

    List<ForwardTestResult> findByWasCorrectIsNull();

    @Query("SELECT ft FROM ForwardTestResult ft WHERE ft.wasCorrect IS NULL " +
           "AND ft.predictionDate <= :evaluationThreshold ORDER BY ft.predictionDate ASC")
    List<ForwardTestResult> findPendingEvaluation(@Param("evaluationThreshold") LocalDate evaluationThreshold);

    @Query("SELECT ft FROM ForwardTestResult ft WHERE ft.instrument.id = :instrumentId " +
           "AND ft.wasCorrect IS NOT NULL ORDER BY ft.predictionDate DESC")
    List<ForwardTestResult> findEvaluatedByInstrumentId(@Param("instrumentId") Long instrumentId);

    @Query("SELECT COUNT(ft) FROM ForwardTestResult ft WHERE ft.instrument.id = :instrumentId AND ft.wasCorrect IS NOT NULL")
    long countEvaluatedByInstrumentId(@Param("instrumentId") Long instrumentId);

    @Query("SELECT COUNT(ft) FROM ForwardTestResult ft WHERE ft.instrument.id = :instrumentId AND ft.wasCorrect = true")
    long countCorrectByInstrumentId(@Param("instrumentId") Long instrumentId);

    @Query("SELECT COUNT(ft) FROM ForwardTestResult ft WHERE ft.instrument.id = :instrumentId " +
           "AND ft.predictedDirection = :direction AND ft.wasCorrect IS NOT NULL")
    long countByInstrumentIdAndDirection(
            @Param("instrumentId") Long instrumentId,
            @Param("direction") PredictionDirection direction);

    @Query("SELECT COUNT(ft) FROM ForwardTestResult ft WHERE ft.instrument.id = :instrumentId " +
           "AND ft.predictedDirection = :direction AND ft.wasCorrect = true")
    long countCorrectByInstrumentIdAndDirection(
            @Param("instrumentId") Long instrumentId,
            @Param("direction") PredictionDirection direction);

    @Query("SELECT AVG(ft.confidenceScore) FROM ForwardTestResult ft " +
           "WHERE ft.instrument.id = :instrumentId AND ft.wasCorrect = true")
    Double findAvgConfidenceWhenCorrect(@Param("instrumentId") Long instrumentId);

    @Query("SELECT ft FROM ForwardTestResult ft ORDER BY ft.createdAt DESC LIMIT :limit")
    List<ForwardTestResult> findRecent(@Param("limit") int limit);

    @Query("SELECT ft FROM ForwardTestResult ft WHERE ft.predictionDate BETWEEN :start AND :end " +
           "ORDER BY ft.predictionDate DESC")
    List<ForwardTestResult> findByPredictionDateRange(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT COUNT(ft) FROM ForwardTestResult ft WHERE ft.wasCorrect IS NOT NULL")
    long countAllEvaluated();

    @Query("SELECT COUNT(ft) FROM ForwardTestResult ft WHERE ft.wasCorrect = true")
    long countAllCorrect();
}
