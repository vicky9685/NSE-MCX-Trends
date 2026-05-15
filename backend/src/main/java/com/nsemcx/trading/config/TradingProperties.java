package com.nsemcx.trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Strongly-typed configuration properties bound from application.yml prefix "trading".
 * All nested structures use Java 21 records for immutability and pattern-matching support.
 */
@ConfigurationProperties(prefix = "trading")
public record TradingProperties(
        MarketHours marketHours,
        Risk risk,
        Instruments instruments,
        Backtesting backtesting,
        PaperTrading paperTrading,
        ForwardTesting forwardTesting,
        Scheduler scheduler
) {

    /**
     * NSE is open 09:15–15:30 IST Mon–Fri.
     * MCX is open 09:00–23:30 IST Mon–Fri.
     */
    public record MarketHours(
            @DefaultValue("09:15") String nseOpen,
            @DefaultValue("15:30") String nseClose,
            @DefaultValue("09:00") String mcxOpen,
            @DefaultValue("23:30") String mcxClose,
            @DefaultValue("Asia/Kolkata") String timezone
    ) {}

    /**
     * Risk management defaults applied when no explicit values are provided.
     */
    public record Risk(
            @DefaultValue("2.0")  double defaultRiskPercent,
            @DefaultValue("10.0") double maxPortfolioRisk,
            @DefaultValue("0.95") double varConfidence
    ) {}

    /**
     * Instrument lists sourced from Yahoo Finance symbols.
     */
    public record Instruments(
            List<String> nse,
            List<String> mcx
    ) {}

    /**
     * Parameters for historical back-testing simulations.
     */
    public record Backtesting(
            @DefaultValue("1000000") double defaultCapital,
            @DefaultValue("0.03")    double commissionPercent,
            @DefaultValue("0.05")    double slippagePercent,
            @DefaultValue("20.0")    double maxDrawdownLimit
    ) {}

    /**
     * Paper-trading (simulated live trading) configuration.
     */
    public record PaperTrading(
            @DefaultValue("false")   boolean enabled,
            @DefaultValue("500000")  double initialCapital,
            @DefaultValue("10")      int maxPositions,
            @DefaultValue("50000")   double maxCapitalPerTrade
    ) {}

    /**
     * Forward-testing (walk-forward analysis) configuration.
     */
    public record ForwardTesting(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("30")    int lookAheadDays
    ) {}

    /**
     * Scheduler timing overrides.  All values are also individually
     * configurable via environment-specific application.yml.
     */
    public record Scheduler(
            @DefaultValue("0 30 6 * * MON-FRI") String historicalCron,
            @DefaultValue("900000")  long technicalRateMs,
            @DefaultValue("1800000") long signalRateMs,
            @DefaultValue("60000")   long alertRateMs,
            @DefaultValue("60000")   long paperRateMs
    ) {}
}
