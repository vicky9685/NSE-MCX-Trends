package com.nsemcx.trading.repository;

import com.nsemcx.trading.domain.SignalStatus;
import com.nsemcx.trading.domain.SignalType;
import com.nsemcx.trading.model.TradeSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TradeSignalRepository extends JpaRepository<TradeSignal, Long> {

    List<TradeSignal> findByStatusOrderByCreatedAtDesc(SignalStatus status);

    List<TradeSignal> findByInstrumentIdAndStatusOrderByCreatedAtDesc(Long instrumentId, SignalStatus status);

    @Query("SELECT ts FROM TradeSignal ts WHERE ts.instrument.symbol = :symbol AND ts.status = :status ORDER BY ts.createdAt DESC")
    List<TradeSignal> findBySymbolAndStatus(@Param("symbol") String symbol, @Param("status") SignalStatus status);

    @Query("SELECT ts FROM TradeSignal ts WHERE ts.status = 'ACTIVE' ORDER BY ts.confidence DESC, ts.createdAt DESC")
    List<TradeSignal> findAllActiveOrderByConfidence();

    @Query("SELECT ts FROM TradeSignal ts WHERE ts.status = 'ACTIVE' AND ts.expiresAt < :now")
    List<TradeSignal> findExpiredActiveSignals(@Param("now") OffsetDateTime now);

    @Query("SELECT ts FROM TradeSignal ts WHERE ts.instrument.id = :instrumentId ORDER BY ts.createdAt DESC LIMIT :limit")
    List<TradeSignal> findLatestByInstrumentId(@Param("instrumentId") Long instrumentId, @Param("limit") int limit);

    @Query("SELECT ts FROM TradeSignal ts WHERE ts.signalType IN :types AND ts.status = 'ACTIVE' ORDER BY ts.confidence DESC")
    List<TradeSignal> findActiveBySignalTypes(@Param("types") List<SignalType> types);

    @Modifying
    @Transactional
    @Query("UPDATE TradeSignal ts SET ts.status = 'EXPIRED', ts.triggeredAt = :now WHERE ts.status = 'ACTIVE' AND ts.expiresAt < :now")
    int expireOldSignals(@Param("now") OffsetDateTime now);

    @Query("SELECT ts FROM TradeSignal ts WHERE ts.createdAt >= :since ORDER BY ts.confidence DESC")
    List<TradeSignal> findSignalsCreatedAfter(@Param("since") OffsetDateTime since);

    @Query("SELECT AVG(ts.confidence) FROM TradeSignal ts WHERE ts.instrument.id = :instrumentId AND ts.status IN ('TARGET_HIT', 'STOPLOSS_HIT')")
    Double findAverageConfidenceByInstrumentId(@Param("instrumentId") Long instrumentId);

    long countByStatus(SignalStatus status);
}
