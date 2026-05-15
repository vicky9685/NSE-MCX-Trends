package com.nsemcx.trading.repository;

import com.nsemcx.trading.model.BacktestTrade;
import com.nsemcx.trading.model.BacktestTrade.ExitReason;
import com.nsemcx.trading.model.BacktestTrade.TradeDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface BacktestTradeRepository extends JpaRepository<BacktestTrade, Long> {

    List<BacktestTrade> findByBacktestResultIdOrderByEntryDateAsc(Long backtestResultId);

    Page<BacktestTrade> findByBacktestResultId(Long backtestResultId, Pageable pageable);

    List<BacktestTrade> findByBacktestResultIdAndDirection(Long backtestResultId, TradeDirection direction);

    List<BacktestTrade> findByBacktestResultIdAndExitReason(Long backtestResultId, ExitReason exitReason);

    @Query("SELECT bt FROM BacktestTrade bt WHERE bt.backtestResult.id = :resultId AND bt.pnl > 0 ORDER BY bt.pnl DESC")
    List<BacktestTrade> findWinningTradesByResultId(@Param("resultId") Long resultId);

    @Query("SELECT bt FROM BacktestTrade bt WHERE bt.backtestResult.id = :resultId AND bt.pnl < 0 ORDER BY bt.pnl ASC")
    List<BacktestTrade> findLosingTradesByResultId(@Param("resultId") Long resultId);

    @Query("SELECT bt FROM BacktestTrade bt WHERE bt.backtestResult.id = :resultId AND bt.entryDate BETWEEN :start AND :end ORDER BY bt.entryDate ASC")
    List<BacktestTrade> findByResultIdAndDateRange(
            @Param("resultId") Long resultId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT AVG(bt.pnl) FROM BacktestTrade bt WHERE bt.backtestResult.id = :resultId AND bt.pnl > 0")
    BigDecimal findAvgWinByResultId(@Param("resultId") Long resultId);

    @Query("SELECT AVG(bt.pnl) FROM BacktestTrade bt WHERE bt.backtestResult.id = :resultId AND bt.pnl < 0")
    BigDecimal findAvgLossByResultId(@Param("resultId") Long resultId);

    @Query("SELECT SUM(bt.pnl) FROM BacktestTrade bt WHERE bt.backtestResult.id = :resultId AND bt.pnl > 0")
    BigDecimal findGrossProfitByResultId(@Param("resultId") Long resultId);

    @Query("SELECT SUM(bt.pnl) FROM BacktestTrade bt WHERE bt.backtestResult.id = :resultId AND bt.pnl < 0")
    BigDecimal findGrossLossByResultId(@Param("resultId") Long resultId);

    @Query("SELECT AVG(bt.holdingDays) FROM BacktestTrade bt WHERE bt.backtestResult.id = :resultId")
    Double findAvgHoldingDaysByResultId(@Param("resultId") Long resultId);

    @Query("SELECT COUNT(bt) FROM BacktestTrade bt WHERE bt.backtestResult.id = :resultId AND bt.pnl > 0")
    long countWinsByResultId(@Param("resultId") Long resultId);

    @Query("SELECT COUNT(bt) FROM BacktestTrade bt WHERE bt.backtestResult.id = :resultId AND bt.pnl <= 0")
    long countLossesByResultId(@Param("resultId") Long resultId);

    long countByBacktestResultId(Long backtestResultId);

    void deleteByBacktestResultId(Long backtestResultId);
}
