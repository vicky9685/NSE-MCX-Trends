package com.nsemcx.trading.repository;

import com.nsemcx.trading.model.PaperTrade;
import com.nsemcx.trading.model.PaperTrade.PaperTradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Portfolio-level queries across paper trades.
 * Extends PaperTrade as the backing entity since paper trades represent simulated positions.
 * For a live-broker portfolio, a separate LivePosition entity would be used.
 */
@Repository
public interface PortfolioPositionRepository extends JpaRepository<PaperTrade, Long> {

    @Query("SELECT pt FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'OPEN' ORDER BY pt.entryTime ASC")
    List<PaperTrade> findOpenPositionsBySession(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(DISTINCT pt.instrument.id) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'OPEN'")
    long countDistinctInstrumentsInOpenPositions(@Param("sessionId") String sessionId);

    @Query("SELECT SUM(pt.entryPrice * pt.quantity) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'OPEN'")
    BigDecimal sumCapitalDeployed(@Param("sessionId") String sessionId);

    @Query("SELECT SUM(pt.pnl) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'CLOSED' AND pt.pnl > 0")
    BigDecimal sumGrossProfit(@Param("sessionId") String sessionId);

    @Query("SELECT SUM(pt.pnl) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'CLOSED' AND pt.pnl < 0")
    BigDecimal sumGrossLoss(@Param("sessionId") String sessionId);

    @Query("SELECT MIN(pt.pnl) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'CLOSED'")
    BigDecimal findWorstTradePnl(@Param("sessionId") String sessionId);

    @Query("SELECT MAX(pt.pnl) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'CLOSED'")
    BigDecimal findBestTradePnl(@Param("sessionId") String sessionId);

    @Query("SELECT pt FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = :status " +
           "AND pt.entryTime BETWEEN :from AND :to ORDER BY pt.entryTime DESC")
    List<PaperTrade> findBySessionAndStatusAndTimeRange(
            @Param("sessionId") String sessionId,
            @Param("status") PaperTradeStatus status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query("SELECT pt.instrument.id, SUM(pt.pnl) FROM PaperTrade pt WHERE pt.sessionId = :sessionId " +
           "AND pt.status = 'CLOSED' GROUP BY pt.instrument.id ORDER BY SUM(pt.pnl) DESC")
    List<Object[]> findPnlByInstrumentForSession(@Param("sessionId") String sessionId);
}
