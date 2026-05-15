package com.nsemcx.trading.repository;

import com.nsemcx.trading.model.PaperTrade;
import com.nsemcx.trading.model.PaperTrade.PaperTradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaperTradeRepository extends JpaRepository<PaperTrade, Long> {

    List<PaperTrade> findBySessionIdOrderByEntryTimeDesc(String sessionId);

    List<PaperTrade> findBySessionIdAndStatusOrderByEntryTimeDesc(String sessionId, PaperTradeStatus status);

    List<PaperTrade> findBySessionIdAndStatus(String sessionId, PaperTradeStatus status);

    List<PaperTrade> findByInstrumentIdAndSessionId(Long instrumentId, String sessionId);

    Optional<PaperTrade> findByIdAndSessionId(Long id, String sessionId);

    @Query("SELECT pt FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'OPEN' ORDER BY pt.entryTime ASC")
    List<PaperTrade> findOpenPositions(@Param("sessionId") String sessionId);

    @Query("SELECT pt FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'CLOSED' ORDER BY pt.exitTime DESC")
    List<PaperTrade> findClosedTrades(@Param("sessionId") String sessionId);

    @Query("SELECT SUM(pt.pnl) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'CLOSED'")
    BigDecimal findTotalRealizedPnl(@Param("sessionId") String sessionId);

    @Query("SELECT SUM(pt.pnl) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'OPEN'")
    BigDecimal findTotalUnrealizedPnl(@Param("sessionId") String sessionId);

    @Query("SELECT SUM(pt.commission) FROM PaperTrade pt WHERE pt.sessionId = :sessionId")
    BigDecimal findTotalCommission(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(pt) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'CLOSED' AND pt.pnl > 0")
    long countWinningTrades(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(pt) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'CLOSED' AND pt.pnl <= 0")
    long countLosingTrades(@Param("sessionId") String sessionId);

    @Query("SELECT AVG(pt.pnl) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'CLOSED' AND pt.pnl > 0")
    BigDecimal findAvgWin(@Param("sessionId") String sessionId);

    @Query("SELECT AVG(pt.pnl) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'CLOSED' AND pt.pnl < 0")
    BigDecimal findAvgLoss(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(pt) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'OPEN'")
    long countOpenPositions(@Param("sessionId") String sessionId);

    @Query("SELECT SUM(pt.entryPrice * pt.quantity) FROM PaperTrade pt WHERE pt.sessionId = :sessionId AND pt.status = 'OPEN'")
    BigDecimal findTotalCapitalDeployed(@Param("sessionId") String sessionId);

    @Modifying
    @Transactional
    @Query("UPDATE PaperTrade pt SET pt.status = 'CANCELLED', pt.exitReason = 'RESET' WHERE pt.sessionId = :sessionId AND pt.status = 'OPEN'")
    int cancelOpenPositions(@Param("sessionId") String sessionId);

    long countBySessionId(String sessionId);
}
