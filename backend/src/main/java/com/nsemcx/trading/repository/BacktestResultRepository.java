package com.nsemcx.trading.repository;

import com.nsemcx.trading.model.BacktestResult;
import com.nsemcx.trading.model.BacktestResult.BacktestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BacktestResultRepository extends JpaRepository<BacktestResult, Long> {

    List<BacktestResult> findByInstrumentIdOrderByCreatedAtDesc(Long instrumentId);

    List<BacktestResult> findByStrategyNameOrderByCreatedAtDesc(String strategyName);

    List<BacktestResult> findByStatusOrderByCreatedAtDesc(BacktestStatus status);

    List<BacktestResult> findByInstrumentIdAndStrategyNameOrderByCreatedAtDesc(
            Long instrumentId, String strategyName);

    @Query("SELECT br FROM BacktestResult br WHERE br.instrument.id = :instrumentId " +
           "AND br.status = 'COMPLETED' ORDER BY br.totalReturn DESC")
    List<BacktestResult> findCompletedByInstrumentIdOrderByReturnDesc(@Param("instrumentId") Long instrumentId);

    @Query("SELECT br FROM BacktestResult br WHERE br.strategyName = :strategyName " +
           "AND br.status = 'COMPLETED' ORDER BY br.sharpeRatio DESC NULLS LAST")
    List<BacktestResult> findCompletedByStrategyOrderBySharpe(@Param("strategyName") String strategyName);

    @Query("SELECT br FROM BacktestResult br WHERE br.instrument.id = :instrumentId " +
           "AND br.strategyName IN :strategies AND br.status = 'COMPLETED' ORDER BY br.createdAt DESC")
    List<BacktestResult> findByInstrumentAndStrategies(
            @Param("instrumentId") Long instrumentId,
            @Param("strategies") List<String> strategies);

    @Query("SELECT br FROM BacktestResult br WHERE br.status = 'RUNNING' AND br.createdAt < :staleThreshold")
    List<BacktestResult> findStaleRunningBacktests(@Param("staleThreshold") OffsetDateTime staleThreshold);

    @Query("SELECT br FROM BacktestResult br ORDER BY br.createdAt DESC LIMIT :limit")
    List<BacktestResult> findLatestResults(@Param("limit") int limit);

    @Query("SELECT DISTINCT br.strategyName FROM BacktestResult br ORDER BY br.strategyName")
    List<String> findDistinctStrategyNames();

    Optional<BacktestResult> findTopByInstrumentIdAndStatusOrderByTotalReturnDesc(
            Long instrumentId, BacktestStatus status);

    long countByStatus(BacktestStatus status);

    long countByInstrumentId(Long instrumentId);
}
