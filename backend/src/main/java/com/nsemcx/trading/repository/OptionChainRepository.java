package com.nsemcx.trading.repository;

import com.nsemcx.trading.model.OptionChain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OptionChainRepository extends JpaRepository<OptionChain, Long> {

    @Query("SELECT oc FROM OptionChain oc WHERE oc.instrument.id = :instrumentId AND oc.expiry = :expiry ORDER BY oc.strike ASC")
    List<OptionChain> findByInstrumentIdAndExpiry(
            @Param("instrumentId") Long instrumentId,
            @Param("expiry") LocalDate expiry);

    @Query("SELECT oc FROM OptionChain oc WHERE oc.instrument.symbol = :symbol AND oc.expiry = :expiry ORDER BY oc.strike ASC")
    List<OptionChain> findBySymbolAndExpiry(
            @Param("symbol") String symbol,
            @Param("expiry") LocalDate expiry);

    @Query("SELECT oc FROM OptionChain oc WHERE oc.instrument.id = :instrumentId AND oc.expiry >= :today ORDER BY oc.expiry ASC, oc.strike ASC")
    List<OptionChain> findActiveByInstrumentId(
            @Param("instrumentId") Long instrumentId,
            @Param("today") LocalDate today);

    @Query("SELECT oc FROM OptionChain oc WHERE oc.instrument.symbol = :symbol AND oc.timestamp = (SELECT MAX(oc2.timestamp) FROM OptionChain oc2 WHERE oc2.instrument.symbol = :symbol AND oc2.expiry = :expiry) AND oc.expiry = :expiry ORDER BY oc.strike ASC")
    List<OptionChain> findLatestBySymbolAndExpiry(
            @Param("symbol") String symbol,
            @Param("expiry") LocalDate expiry);

    @Query("SELECT DISTINCT oc.expiry FROM OptionChain oc WHERE oc.instrument.id = :instrumentId AND oc.expiry >= :today ORDER BY oc.expiry ASC")
    List<LocalDate> findAvailableExpiriesByInstrumentId(
            @Param("instrumentId") Long instrumentId,
            @Param("today") LocalDate today);

    @Query("SELECT SUM(oc.peOi) / NULLIF(SUM(oc.ceOi), 0) FROM OptionChain oc WHERE oc.instrument.id = :instrumentId AND oc.expiry = :expiry")
    Optional<Double> calculatePCR(
            @Param("instrumentId") Long instrumentId,
            @Param("expiry") LocalDate expiry);

    @Query("SELECT oc.strike FROM OptionChain oc WHERE oc.instrument.id = :instrumentId AND oc.expiry = :expiry GROUP BY oc.strike ORDER BY SUM(oc.ceOi + oc.peOi) DESC LIMIT 1")
    Optional<BigDecimal> findMaxPainStrike(
            @Param("instrumentId") Long instrumentId,
            @Param("expiry") LocalDate expiry);

    @Query("SELECT oc FROM OptionChain oc WHERE oc.instrument.id = :instrumentId AND oc.expiry = :expiry AND (oc.ceChangeOi > :threshold OR oc.peChangeOi > :threshold) ORDER BY oc.strike ASC")
    List<OptionChain> findUnusualOIBuildup(
            @Param("instrumentId") Long instrumentId,
            @Param("expiry") LocalDate expiry,
            @Param("threshold") Long threshold);
}
