package com.nsemcx.trading.repository;

import com.nsemcx.trading.model.TechnicalIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TechnicalIndicatorRepository extends JpaRepository<TechnicalIndicator, Long> {

    @Query("SELECT ti FROM TechnicalIndicator ti WHERE ti.instrument.id = :instrumentId ORDER BY ti.timestamp DESC LIMIT 1")
    Optional<TechnicalIndicator> findLatestByInstrumentId(@Param("instrumentId") Long instrumentId);

    @Query("SELECT ti FROM TechnicalIndicator ti WHERE ti.instrument.symbol = :symbol ORDER BY ti.timestamp DESC LIMIT 1")
    Optional<TechnicalIndicator> findLatestBySymbol(@Param("symbol") String symbol);

    @Query("SELECT ti FROM TechnicalIndicator ti WHERE ti.instrument.id = :instrumentId ORDER BY ti.timestamp DESC LIMIT :limit")
    List<TechnicalIndicator> findLatestNByInstrumentId(@Param("instrumentId") Long instrumentId, @Param("limit") int limit);

    @Query("SELECT ti FROM TechnicalIndicator ti WHERE ti.instrument.id = :instrumentId AND ti.timestamp BETWEEN :startDate AND :endDate ORDER BY ti.timestamp ASC")
    List<TechnicalIndicator> findByInstrumentIdAndDateRange(
            @Param("instrumentId") Long instrumentId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    @Query("SELECT ti FROM TechnicalIndicator ti WHERE ti.instrument.id = :instrumentId AND ti.rsi < :rsiThreshold ORDER BY ti.timestamp DESC LIMIT :limit")
    List<TechnicalIndicator> findOversoldInstances(
            @Param("instrumentId") Long instrumentId,
            @Param("rsiThreshold") double rsiThreshold,
            @Param("limit") int limit);

    @Query("SELECT ti FROM TechnicalIndicator ti WHERE ti.instrument.id = :instrumentId AND ti.rsi > :rsiThreshold ORDER BY ti.timestamp DESC LIMIT :limit")
    List<TechnicalIndicator> findOverboughtInstances(
            @Param("instrumentId") Long instrumentId,
            @Param("rsiThreshold") double rsiThreshold,
            @Param("limit") int limit);

    boolean existsByInstrumentIdAndTimestamp(Long instrumentId, OffsetDateTime timestamp);
}
