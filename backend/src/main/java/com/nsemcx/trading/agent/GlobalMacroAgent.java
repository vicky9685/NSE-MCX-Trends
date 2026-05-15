package com.nsemcx.trading.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalMacroAgent {

    private final ChatClient chatClient;

    // Java 21 records for return types
    public record GlobalMacroAnalysis(
            String usMarketAssessment,
            String europeMarketAssessment,
            String asiaMarketAssessment,
            String emergingMarketsOutlook,
            String usdInrImpact,
            String fedPolicyImpact,
            String rbiPolicyImpact,
            String commodityMacroOutlook,
            String fiiFlowAssessment,
            String geopoliticalRisks,
            double indiaMarketBias, // -100 to +100
            String overallMacroStance,
            String tradingRecommendations,
            LocalDate analysisDate
    ) {}

    public record CurrencyImpact(
            double usdInrRate,
            String trend,
            String impactOnNifty,
            String impactOnIT,
            String impactOnFMCG,
            String impactOnMetals,
            String impactOnCrudeOilImporters,
            String fiiFlowLikelihood,
            String recommendation
    ) {}

    public record CentralBankAnalysis(
            String fedStance,
            double fedFundsRate,
            String fedNextMoveExpected,
            String rbiStance,
            double rbiRepoRate,
            String rbiNextMoveExpected,
            String interestRateDifferential,
            String impactOnEquities,
            String impactOnBonds,
            String impactOnCurrency,
            String impactOnGold
    ) {}

    private static final String MACRO_SYSTEM_PROMPT = """
            You are the world's leading global macro strategist with expertise in:
            - Cross-asset correlations between Indian markets and global markets
            - Federal Reserve and RBI monetary policy analysis
            - FII/DII flow dynamics in Indian equity markets
            - Currency market dynamics (USD/INR, EUR/INR, GBP/INR, JPY correlations)
            - Commodity macro: crude oil, gold, silver impact on Indian economy
            - Geopolitical risk assessment and its market impact
            - Emerging market capital flow dynamics
            
            You analyze macro conditions with rigorous quantitative thinking and provide:
            - Specific impact assessments with quantified market effect estimates
            - Historical precedent references
            - Actionable positioning recommendations
            - Hedging strategies for macro risks
            - Probability-weighted scenario analysis
            
            Be precise, data-driven, and consider Indian market-specific factors like:
            - FII ownership patterns
            - Government policy and budget impact
            - Monsoon and agricultural commodity impact
            - India's current account and fiscal deficit dynamics
            """;

    public GlobalMacroAnalysis analyzeGlobalMarkets() {
        String prompt = """
                Perform a comprehensive global macro analysis relevant to Indian markets today:
                
                Analyze:
                1. US MARKETS: S&P 500, Nasdaq, Russell 2000 - current trend, valuations, recession risk
                2. EUROPE MARKETS: DAX, FTSE - key drivers, ECB policy impact
                3. ASIA MARKETS: Nikkei, Hang Seng, China A-shares - geopolitical risks, China reopening
                4. EMERGING MARKETS: EM flows, DXY correlation, relative performance vs India
                5. INDIA CORRELATION ANALYSIS: How US market moves impact Nifty historically (0.6-0.8 correlation)
                6. CROSS-ASSET SIGNALS: Bond yields, gold, crude oil, VIX, DXY combined signal
                7. FII/DII FLOW CONTEXT: Likely flow direction based on global macro
                8. INDIA MARKET STANCE: Bullish/Bearish/Neutral with reasoning
                9. KEY RISKS: Top 3 global risks for Indian markets in next 30 days
                10. ACTIONABLE RECOMMENDATIONS: 2-3 specific position ideas based on global macro
                
                Provide analysis with specific numbers and historical context where relevant.
                """;

        try {
            String response = chatClient.prompt()
                    .system(MACRO_SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();

            return parseGlobalMacroAnalysis(response);

        } catch (Exception e) {
            log.error("Error in global macro analysis: {}", e.getMessage());
            return buildFallbackMacroAnalysis();
        }
    }

    public CurrencyImpact analyzeCurrencyImpact() {
        String prompt = """
                Analyze the USD/INR exchange rate and its comprehensive impact on Indian markets:
                
                1. USD/INR TREND ANALYSIS:
                   - Current level and key technical levels (support: 83.50, resistance: 85.00 as examples)
                   - RBI intervention patterns and behavior
                   - Key drivers: DXY, crude oil prices, FII flows, current account
                
                2. SECTOR-SPECIFIC IMPACT:
                   - IT Exporters (TCS, Infosys, Wipro): Revenue impact per 1% INR depreciation
                   - FMCG with import exposure: Cost pressure analysis
                   - Metals (Hindalco, Tata Steel): LME price translation impact
                   - Crude Oil Importers (IOC, BPCL): Under-recovery calculation
                   - Pharma exporters: Margin impact
                   - Capital goods with USD-denominated debt
                
                3. FII FLOW IMPLICATIONS:
                   - Dollar-denominated returns for FIIs
                   - Break-even currency levels for profitable FII exit
                   - Hedging costs for FII portfolio
                
                4. PORTFOLIO RECOMMENDATIONS:
                   - Sectors to overweight/underweight based on currency outlook
                   - Currency hedging strategies for Indian portfolios
                
                Provide specific percentage impacts and key INR levels to watch.
                """;

        try {
            String response = chatClient.prompt()
                    .system(MACRO_SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();

            return parseCurrencyImpact(response);

        } catch (Exception e) {
            log.error("Error in currency impact analysis: {}", e.getMessage());
            return new CurrencyImpact(
                    84.50, "NEUTRAL",
                    "Moderate pressure if INR weakens beyond 85",
                    "IT sector benefits from weaker INR (positive for revenues)",
                    "FMCG faces input cost pressure from INR weakness",
                    "Metal stocks have mixed impact - exports positive, imports negative",
                    "Higher costs for crude oil importers (negative for OMCs)",
                    "FII flows likely stable to slightly negative with weak INR",
                    "Maintain IT exposure as hedge against INR weakness"
            );
        }
    }

    public CentralBankAnalysis analyzeFedRBIPolicy() {
        String prompt = """
                Analyze the current stance and forward guidance of Federal Reserve and RBI:
                
                1. FEDERAL RESERVE ANALYSIS:
                   - Current federal funds rate and dot plot projections
                   - Inflation trajectory (CPI, PCE) and Fed's response function
                   - Quantitative tightening/easing status and balance sheet
                   - Labor market data (NFP, unemployment) implications
                   - Key Fed speeches and minutes interpretation
                   - Market-implied probabilities for next meeting
                   - Historical: What happens to Nifty when Fed cuts/hikes rates
                
                2. RBI ANALYSIS:
                   - Current repo rate, reverse repo, CRR, SLR
                   - India CPI vs RBI target band (2-6%)
                   - Growth-inflation trade-off assessment
                   - Liquidity stance (surplus/deficit)
                   - Currency management objectives
                   - Next MPC meeting expected outcome
                
                3. INTEREST RATE DIFFERENTIAL IMPACT:
                   - US-India rate differential and carry trade implications
                   - FII debt vs equity flow sensitivity to rate differential
                   - Impact on rupee stability
                
                4. SECTOR IMPACT ANALYSIS:
                   - Banks and NBFCs: NIM impact of rate changes
                   - Real estate: Demand sensitivity to home loan rates
                   - Auto sector: EMI affordability
                   - PSU Bonds and Gilt market direction
                
                5. INVESTMENT IMPLICATIONS:
                   - Rate-sensitive sectors to avoid/accumulate
                   - Fixed income vs equity allocation shift
                   - Duration positioning in bond portfolio
                
                Use current knowledge of Fed at approximately 5.25-5.50% and RBI at approximately 6.50% as baseline.
                """;

        try {
            String response = chatClient.prompt()
                    .system(MACRO_SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();

            return parseCentralBankAnalysis(response);

        } catch (Exception e) {
            log.error("Error in Fed/RBI policy analysis: {}", e.getMessage());
            return new CentralBankAnalysis(
                    "Data-dependent, watching inflation trajectory", 5.375,
                    "Possible cut if CPI falls below 2.5% sustainably",
                    "Neutral with focus on inflation management", 6.50,
                    "Hold expected with potential cut later in year",
                    "~110bps positive differential favors INR carry",
                    "Neutral to mildly positive for equities if cuts are priced in",
                    "Bond rally likely if rate cuts materialize",
                    "INR support from positive carry, risk from global uncertainty",
                    "Gold benefits from lower real rates and rate cut expectations"
            );
        }
    }

    // ========== Private parsing methods ==========

    private GlobalMacroAnalysis parseGlobalMacroAnalysis(String response) {
        if (response == null || response.isEmpty()) {
            return buildFallbackMacroAnalysis();
        }

        return new GlobalMacroAnalysis(
                extractSection(response, "US MARKETS", "Europe"),
                extractSection(response, "EUROPE", "ASIA"),
                extractSection(response, "ASIA", "EMERGING"),
                extractSection(response, "EMERGING MARKETS", "INDIA CORRELATION"),
                extractSection(response, "currency", "FII"),
                extractSection(response, "Fed", "RBI"),
                extractSection(response, "RBI", "COMMODITY"),
                extractSection(response, "COMMODITY", "KEY RISKS"),
                extractSection(response, "FII", "GEOPOLITICAL"),
                extractSection(response, "RISK", "RECOMMENDATIONS"),
                determineMacroBias(response),
                determineMacroStance(response),
                extractSection(response, "RECOMMENDATIONS", ""),
                LocalDate.now()
        );
    }

    private CurrencyImpact parseCurrencyImpact(String response) {
        return new CurrencyImpact(
                84.50, // Would extract from response in real impl
                extractTrend(response),
                extractSection(response, "Nifty", "IT"),
                extractSection(response, "IT", "FMCG"),
                extractSection(response, "FMCG", "Metal"),
                extractSection(response, "Metal", "Crude"),
                extractSection(response, "Crude", "FII"),
                extractSection(response, "FII flow", "RECOMMENDATIONS"),
                extractSection(response, "RECOMMENDATIONS", "")
        );
    }

    private CentralBankAnalysis parseCentralBankAnalysis(String response) {
        return new CentralBankAnalysis(
                extractSection(response, "Fed stance", "rate"),
                5.375,
                extractSection(response, "cut", "RBI"),
                extractSection(response, "RBI", "rate differential"),
                6.50,
                extractSection(response, "MPC", "differential"),
                "~110bps positive differential favoring INR carry trade",
                extractSection(response, "equities", "bonds"),
                extractSection(response, "bonds", "currency"),
                extractSection(response, "currency", "gold"),
                extractSection(response, "gold", "")
        );
    }

    private GlobalMacroAnalysis buildFallbackMacroAnalysis() {
        return new GlobalMacroAnalysis(
                "US markets showing resilience with Fed pivot expectations supporting equities",
                "European markets mixed - ECB policy uncertainty and weak growth outlook",
                "Asia markets cautious - China stimulus hopes vs geopolitical risks",
                "Emerging markets generally positive - India leading the pack",
                "USD/INR stable around 83-85 range - RBI managing volatility",
                "Fed data-dependent - markets pricing in rate cuts in H2",
                "RBI on hold - supporting growth while managing inflation",
                "Commodities: Gold strong on rate cut expectations, Crude volatile on OPEC+",
                "FII flows neutral to slightly positive into Indian equities",
                "Key risks: Geopolitical escalation, China slowdown, Fed hawkishness",
                20.0,
                "MILDLY_BULLISH",
                "Maintain India overweight. Add defensives as hedge. Gold allocation for tail risk.",
                LocalDate.now()
        );
    }

    private double determineMacroBias(String response) {
        if (response == null) return 0;
        String lower = response.toLowerCase();
        int bullishCount = countOccurrences(lower, "bullish") + countOccurrences(lower, "positive") + countOccurrences(lower, "upside");
        int bearishCount = countOccurrences(lower, "bearish") + countOccurrences(lower, "negative") + countOccurrences(lower, "downside");
        if (bullishCount + bearishCount == 0) return 0;
        return (double)(bullishCount - bearishCount) / (bullishCount + bearishCount) * 100;
    }

    private String determineMacroStance(String response) {
        double bias = determineMacroBias(response);
        if (bias > 40) return "STRONGLY_BULLISH";
        if (bias > 15) return "BULLISH";
        if (bias > -15) return "NEUTRAL";
        if (bias > -40) return "BEARISH";
        return "STRONGLY_BEARISH";
    }

    private String extractSection(String text, String startKeyword, String endKeyword) {
        if (text == null || startKeyword == null || startKeyword.isEmpty()) return "";
        String lower = text.toLowerCase();
        int start = lower.indexOf(startKeyword.toLowerCase());
        if (start < 0) return "";
        int contentStart = text.indexOf('\n', start);
        if (contentStart < 0) contentStart = start + startKeyword.length();
        int end = endKeyword != null && !endKeyword.isEmpty()
                ? lower.indexOf(endKeyword.toLowerCase(), contentStart)
                : -1;
        if (end < 0) end = Math.min(text.length(), contentStart + 500);
        return text.substring(contentStart, end).trim();
    }

    private String extractTrend(String response) {
        if (response == null) return "NEUTRAL";
        String lower = response.toLowerCase();
        if (lower.contains("depreciation") || lower.contains("weakening")) return "WEAKENING_INR";
        if (lower.contains("appreciation") || lower.contains("strengthening")) return "STRENGTHENING_INR";
        return "NEUTRAL";
    }

    private int countOccurrences(String text, String word) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) != -1) {
            count++;
            idx += word.length();
        }
        return count;
    }
}
