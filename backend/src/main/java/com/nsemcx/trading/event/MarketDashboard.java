package com.nsemcx.trading.event;

import com.nsemcx.trading.domain.Exchange;
import com.nsemcx.trading.domain.SignalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Full market dashboard snapshot returned by the Orchestrator agent and
 * served by GET /api/dashboard.
 * Also broadcast to WebSocket topic /topic/dashboard on each analysis cycle.
 */
public record MarketDashboard(

        /** ISO-8601 timestamp of when this snapshot was generated. */
        Instant generatedAt,

        /** Date this analysis covers (IST). */
        LocalDate analysisDate,

        /** Overall market sentiment score 0–100 (50 = neutral). */
        double marketSentimentScore,

        /** Textual sentiment label derived from the score. */
        String marketSentiment,

        /** India VIX proxy / volatility outlook. */
        VixOutlook vixOutlook,

        /** Nifty 50 summary. */
        IndexSummary nifty50,

        /** Bank Nifty summary. */
        IndexSummary bankNifty,

        /** MCX summary data points. */
        McxSummary mcxSummary,

        /** FII/DII institutional flow data. */
        InstitutionalFlow institutionalFlow,

        /** Top NSE equity signals (STRONG_BUY / BUY). */
        List<SignalSummary> topNseSignals,

        /** Top MCX commodity signals. */
        List<SignalSummary> topMcxSignals,

        /** Top options setups (CE/PE/spread). */
        List<OptionsSetup> topOptionsSetups,

        /** Sector performance leaders and laggards. */
        SectorAnalysis sectorAnalysis,

        /** Global macro factors affecting Indian markets. */
        GlobalMacroSummary globalMacro,

        /** Current risk environment assessment. */
        RiskEnvironment riskEnvironment,

        /** Key levels to watch (supports, resistances, pivot). */
        KeyLevels keyLevels,

        /** Active signals count breakdown. */
        SignalStats signalStats,

        /** Model-generated trade ideas (top picks). */
        List<TopPick> topPicks,

        /** Any system warnings or important notes. */
        List<String> warnings

) {

    // ── Nested records ────────────────────────────────────────────────────────

    public record VixOutlook(
            double vixValue,
            String trend,            // RISING / FALLING / STABLE
            String volatilityRegime, // LOW / NORMAL / HIGH / EXTREME
            String outlook,
            String interpretation
    ) {}

    public record IndexSummary(
            String name,
            String symbol,
            double lastClose,
            double change,
            double changePct,
            double dayHigh,
            double dayLow,
            long volume,
            double ema20,
            double ema50,
            double ema200,
            double rsi,
            String trend,            // BULLISH / BEARISH / SIDEWAYS
            String supportLevel,
            String resistanceLevel
    ) {}

    public record McxSummary(
            double goldPrice,
            double goldChangePct,
            double silverPrice,
            double silverChangePct,
            double crudeOilPrice,
            double crudeOilChangePct,
            double naturalGasPrice,
            double naturalGasChangePct,
            String commodityOutlook
    ) {}

    public record InstitutionalFlow(
            double fiiNetBuying,       // positive = buying, negative = selling (crores)
            double diiNetBuying,
            double fiiNetFutures,
            double fiiNetOptions,
            LocalDate date,
            String flowInterpretation,
            String trend               // 3-day trend: CONSISTENT_BUYING / CONSISTENT_SELLING / MIXED
    ) {}

    public record SignalSummary(
            Long signalId,
            String symbol,
            String instrumentName,
            Exchange exchange,
            SignalType signalType,
            double confidence,
            double entryPrice,
            double target1,
            double target2,
            double stoploss,
            double riskReward,
            String timeHorizon,
            String reasoning
    ) {}

    public record OptionsSetup(
            String symbol,
            String setupType,           // CALL_BUY / PUT_BUY / BULL_SPREAD / BEAR_SPREAD / STRADDLE
            double strikePrice,
            String expiry,
            double premium,
            double maxProfit,
            double maxLoss,
            double breakeven,
            double confidence,
            String rationale
    ) {}

    public record SectorAnalysis(
            List<SectorPerformance> leaders,
            List<SectorPerformance> laggards,
            String topSector,
            String weakestSector,
            Map<String, Double> sectorReturns   // sector -> 1-day return %
    ) {}

    public record SectorPerformance(
            String sector,
            double returnPct,
            String trend,
            List<String> topStocks
    ) {}

    public record GlobalMacroSummary(
            double usdInrRate,
            double usdInrChangePct,
            double us10YearYield,
            double crudeOilWti,
            double dxyIndex,          // US Dollar Index
            double sp500ChangePct,
            double sgxNiftyChangePct,
            String globalSentiment,
            String macroImpactOnIndia,
            List<String> keyMacroEvents
    ) {}

    public record RiskEnvironment(
            String overallRisk,          // LOW / MODERATE / HIGH / EXTREME
            double riskScore,            // 0–100
            boolean isEventRisk,         // earnings / RBI / budget etc.
            List<String> riskFactors,
            String positionSizingAdvice,
            double suggestedMaxCapitalDeployment  // % of portfolio
    ) {}

    public record KeyLevels(
            String symbol,
            double pivot,
            double r1, double r2, double r3,
            double s1, double s2, double s3,
            double weeklyHigh, double weeklyLow,
            double monthlyHigh, double monthlyLow,
            double fiftyTwoWeekHigh, double fiftyTwoWeekLow
    ) {}

    public record SignalStats(
            int totalActive,
            int strongBuy,
            int buy,
            int neutral,
            int sell,
            int strongSell,
            int optionsSetups,
            int triggeredToday,
            int targetHitToday,
            int slHitToday
    ) {}

    public record TopPick(
            int rank,
            String symbol,
            String instrumentName,
            SignalType signalType,
            double confidence,
            double entryPrice,
            double target,
            double stoploss,
            double riskReward,
            String catalyst,         // WHY this pick now
            String timeHorizon,
            BigDecimal fundamentalScore,
            BigDecimal technicalScore,
            BigDecimal macroScore
    ) {}

    // ── Derived helpers ──────────────────────────────────────────────────────

    public boolean isBullishMarket() {
        return marketSentimentScore >= 60;
    }

    public boolean isBearishMarket() {
        return marketSentimentScore <= 40;
    }

    public boolean isHighVolatility() {
        return vixOutlook != null &&
               ("HIGH".equals(vixOutlook.volatilityRegime()) || "EXTREME".equals(vixOutlook.volatilityRegime()));
    }

    public int totalSignals() {
        return signalStats != null ? signalStats.totalActive() : 0;
    }
}
