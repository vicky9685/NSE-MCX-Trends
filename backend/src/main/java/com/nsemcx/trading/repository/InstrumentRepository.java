package com.nsemcx.trading.repository;

import com.nsemcx.trading.domain.Exchange;
import com.nsemcx.trading.domain.Segment;
import com.nsemcx.trading.model.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    Optional<Instrument> findBySymbol(String symbol);

    Optional<Instrument> findBySymbolAndIsActiveTrue(String symbol);

    List<Instrument> findByExchangeAndIsActiveTrue(Exchange exchange);

    List<Instrument> findBySegmentAndIsActiveTrue(Segment segment);

    List<Instrument> findByIsActiveTrue();

    List<Instrument> findByExchangeAndSegmentAndIsActiveTrue(Exchange exchange, Segment segment);

    @Query("SELECT i FROM Instrument i WHERE i.isActive = true ORDER BY i.exchange, i.segment, i.symbol")
    List<Instrument> findAllActiveOrderedByExchangeAndSegment();

    @Query("SELECT i FROM Instrument i WHERE UPPER(i.symbol) LIKE UPPER(CONCAT('%', :keyword, '%')) OR UPPER(i.name) LIKE UPPER(CONCAT('%', :keyword, '%'))")
    List<Instrument> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT i FROM Instrument i WHERE i.exchange IN ('NSE', 'BSE') AND i.segment = 'EQUITY' AND i.isActive = true")
    List<Instrument> findActiveEquityInstruments();

    @Query("SELECT i FROM Instrument i WHERE i.exchange = 'MCX' AND i.segment = 'COMMODITY' AND i.isActive = true")
    List<Instrument> findActiveCommodityInstruments();

    boolean existsBySymbol(String symbol);
}
