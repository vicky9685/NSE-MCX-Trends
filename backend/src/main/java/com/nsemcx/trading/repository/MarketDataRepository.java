package com.nsemcx.trading.repository;

import com.nsemcx.trading.model.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, Long> {

    @Query("SELECT md FROM MarketData md WHERE md.instrument.id = :instrumentId ORDER BY md.timestamp DESC")
    List<MarketData> findByInstrumentIdOrderByTimestampDesc(@Param("instrumentId") Long instrumentId);

    @Query("SELECT md FROM MarketData md WHERE md.instrument.id = :instrumentId ORDER BY md.timestamp DESC LIMIT :limit")
    List<MarketData> findLatestByInstrumentId(@Param("instrumentId") Long instrumentId, @Param("limit") int limit);

    @Query("SELECT md FROM MarketData md WHERE md.instrument.id = :instrumentId AND md.timestamp BETWEEN :startDate AND :endDate ORDER BY md.timestamp ASC")
    List<MarketData> findByInstrumentIdAndDateRange(
            @Param("instrumentId") Long instrumentId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    @Query("SELECT md FROM MarketData md WHERE md.instrument.id = :instrumentId ORDER BY md.timestamp DESC LIMIT 1")
    Optional<MarketData> findLatestByInstrumentId(@Param("instrumentId") Long instrumentId);

    @Query("SELECT md FROM MarketData md WHERE md.instrument.symbol = :symbol ORDER BY md.timestamp DESC LIMIT :limit")
    List<MarketData> findLatestBySymbol(@Param("symbol") String symbol, @Param("limit") int limit);

    @Query("SELECT md FROM MarketData md WHERE md.instrument.symbol = :symbol AND md.timestamp BETWEEN :startDate AND :endDate ORDER BY md.timestamp ASC")
    List<MarketData> findBySymbolAndDateRange(
            @Param("symbol") String symbol,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    @Query("SELECT md FROM MarketData md WHERE md.instrument.symbol = :symbol ORDER BY md.timestamp DESC LIMIT 1")
    Optional<MarketData> findLatestBySymbol(@Param("symbol") String symbol);

    @Query("SELECT COUNT(md) FROM MarketData md WHERE md.instrument.id = :instrumentId")
    long countByInstrumentId(@Param("instrumentId") Long instrumentId);

    @Query(value = "SELECT md.* FROM market_data md WHERE md.instrument_id = :instrumentId AND md.timestamp >= :since ORDER BY md.timestamp ASC", nativeQuery = true)
    List<MarketData> findRecentByInstrumentId(@Param("instrumentId") Long instrumentId, @Param("since") OffsetDateTime since);

    boolean existsByInstrumentIdAndTimestamp(Long instrumentId, OffsetDateTime timestamp);

    @Query("SELECT DISTINCT md.instrument.id FROM MarketData md WHERE md.timestamp >= :since")
    List<Long> findInstrumentIdsWithRecentData(@Param("since") OffsetDateTime since);
}
