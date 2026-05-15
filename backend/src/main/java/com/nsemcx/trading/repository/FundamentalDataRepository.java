package com.nsemcx.trading.repository;

import com.nsemcx.trading.model.FundamentalData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundamentalDataRepository extends JpaRepository<FundamentalData, Long> {

    Optional<FundamentalData> findByInstrumentId(Long instrumentId);

    Optional<FundamentalData> findByInstrumentSymbol(String symbol);

    @Query("SELECT fd FROM FundamentalData fd WHERE fd.roe >= :minRoe ORDER BY fd.roe DESC")
    List<FundamentalData> findByMinRoe(@Param("minRoe") BigDecimal minRoe);

    @Query("SELECT fd FROM FundamentalData fd WHERE fd.roce >= :minRoce ORDER BY fd.roce DESC")
    List<FundamentalData> findByMinRoce(@Param("minRoce") BigDecimal minRoce);

    @Query("SELECT fd FROM FundamentalData fd WHERE fd.debtEquity IS NOT NULL AND fd.debtEquity <= :maxDebtEquity ORDER BY fd.debtEquity ASC")
    List<FundamentalData> findByMaxDebtEquity(@Param("maxDebtEquity") BigDecimal maxDebtEquity);

    @Query("SELECT fd FROM FundamentalData fd WHERE fd.promoterHolding >= :minHolding ORDER BY fd.promoterHolding DESC")
    List<FundamentalData> findByMinPromoterHolding(@Param("minHolding") BigDecimal minHolding);

    @Query("SELECT fd FROM FundamentalData fd WHERE fd.fiiFlow > 0 ORDER BY fd.fiiFlow DESC")
    List<FundamentalData> findPositiveFiiFlow();

    @Query("SELECT fd FROM FundamentalData fd WHERE fd.diiFlow > 0 ORDER BY fd.diiFlow DESC")
    List<FundamentalData> findPositiveDiiFlow();

    @Query("SELECT fd FROM FundamentalData fd WHERE fd.revenueGrowth >= :minGrowth AND fd.profitGrowth >= :minGrowth ORDER BY fd.profitGrowth DESC")
    List<FundamentalData> findHighGrowthStocks(
            @Param("minGrowth") BigDecimal minGrowth);

    @Query("SELECT fd FROM FundamentalData fd WHERE fd.peRatio IS NOT NULL AND fd.peRatio <= :maxPe AND fd.roe >= :minRoe ORDER BY fd.peRatio ASC")
    List<FundamentalData> findValueStocks(
            @Param("maxPe") BigDecimal maxPe,
            @Param("minRoe") BigDecimal minRoe);

    boolean existsByInstrumentId(Long instrumentId);
}
