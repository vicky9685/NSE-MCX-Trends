package com.nsemcx.trading.agent;

import com.nsemcx.trading.indicators.TechnicalAnalysisEngine;
import com.nsemcx.trading.model.Instrument;
import com.nsemcx.trading.model.MarketData;
import com.nsemcx.trading.model.TechnicalIndicator;
import com.nsemcx.trading.model.TradeSignal;
import com.nsemcx.trading.domain.SignalType;
import com.nsemcx.trading.domain.SignalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketAnalysisAgent {

    private final ChatClient chatClient;
    private final TechnicalAnalysisEngine technicalEngine;

    // Java 21 records for structured results
    public record MarketContext(
            String symbol,
            double currentPrice,
            double rsi,
            double macd,
            double macdSignal,
            double ema20,
            double ema50,
            double ema200,
            double vwap,
            double bbUpper,
            double bbLower,
            double adx,
            double volumeRatio,
            String candlePattern,
            String trendBias,
            List<MarketData> recentData
    ) {}

    public record ScenarioAnalysis(
            String bullishScenario,
            String bearishScenario,
            String sidewaysScenario,
            String blackSwanScenario,
            double bullishProbability,
            double bearishProbability,
            double sidewaysProbability
    ) {}

    private static final String SYSTEM_PROMPT = """
            You are the world's top institutional trading strategist and quantitative analyst with 30+ years of experience
            in Indian equity markets (NSE/BSE), commodity markets (MCX), and global macro trading.
            
            Your expertise includes:
            - Technical analysis: advanced chart patterns, Elliott Wave theory, Wyckoff methodology, VSA (Volume Spread Analysis)
            - Fundamental analysis: financial statement analysis, sector dynamics, regulatory impact
            - Options strategies: Greeks management, volatility trading, hedging structures
            - MCX commodities: Gold, Silver, Crude Oil, Natural Gas, Copper - all their global drivers
            - Global macro: Fed policy, RBI policy, FII/DII flows, currency impact, geopolitical risks
            - Risk management: position sizing, portfolio heat, VaR, scenario analysis
            
            You provide actionable, specific, data-driven analysis with:
            - Clear entry/exit levels with quantified risk
            - Multiple time horizon views (intraday, swing, positional)
            - Hedging strategies using options
            - Probability-weighted scenario analysis
            - Context-aware recommendations considering India-specific factors
            
            Always think like a seasoned portfolio manager managing institutional capital.
            Be direct, specific, and quantitative. Avoid generic platitudes.
            """;

    public String analyzeMarketConditions(List<MarketData> data, TechnicalIndicator indicators) {
        if (data == null || data.isEmpty()) {
            log.warn("No market data provided for analysis");
            return "Insufficient data for analysis";
        }

        List<Double> closes = technicalEngine.extractClosePrices(data);
        TechnicalAnalysisEngine.CandlePattern pattern = technicalEngine.identifyCandlePattern(data);

        MarketData latest = data.get(data.size() - 1);
        String symbol = latest.getInstrument().getSymbol();

        String userPrompt = buildMarketAnalysisPrompt(symbol, latest, indicators, pattern, closes);

        try {
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
            log.debug("Market analysis completed for {}", symbol);
            return response != null ? response : "Unable to generate analysis";
        } catch (Exception e) {
            log.error("Error calling AI model for market analysis of {}: {}", symbol, e.getMessage());
            return generateFallbackAnalysis(symbol, latest, indicators, pattern);
        }
    }

    public TradeSignal generateTradeSignal(Instrument instrument, MarketContext context) {
        String userPrompt = buildSignalGenerationPrompt(instrument, context);

        try {
            String llmResponse = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            return parseSignalFromLLMResponse(instrument, context, llmResponse);

        } catch (Exception e) {
            log.error("Error generating trade signal for {}: {}", instrument.getSymbol(), e.getMessage());
            return buildDefaultSignal(instrument, context);
        }
    }

    public ScenarioAnalysis scenarioAnalysis(TradeSignal signal) {
        String userPrompt = buildScenarioAnalysisPrompt(signal);

        try {
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            return parseScenarioAnalysis(response);

        } catch (Exception e) {
            log.error("Error in scenario analysis for signal {}: {}", signal.getId(), e.getMessage());
            return new ScenarioAnalysis(
                    "Price breaks above resistance with volume - target " + formatPrice(signal.getTarget1()),
                    "Price breaks below support - stoploss at " + formatPrice(signal.getStoploss()),
                    "Price consolidates between " + formatPrice(signal.getStoploss()) + " and " + formatPrice(signal.getTarget1()),
                    "Extreme event causes 15-20% gap move against position. Circuit breaker triggered.",
                    40, 30, 30
            );
        }
    }

    public MarketContext buildMarketContext(String symbol, List<MarketData> data, TechnicalIndicator indicators) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Cannot build context with empty data");
        }

        MarketData latest = data.get(data.size() - 1);
        List<Double> closes = technicalEngine.extractClosePrices(data);

        double rsi = indicators != null && indicators.getRsi() != null
                ? indicators.getRsi().doubleValue()
                : technicalEngine.calculateRSI(closes, 14);

        TechnicalAnalysisEngine.MACDResult macdResult = technicalEngine.calculateMACD(closes);
        TechnicalAnalysisEngine.CandlePattern pattern = technicalEngine.identifyCandlePattern(data);

        double ema20 = indicators != null && indicators.getEma20() != null
                ? indicators.getEma20().doubleValue()
                : technicalEngine.calculateEMA(closes, 20);

        double ema50 = indicators != null && indicators.getEma50() != null
                ? indicators.getEma50().doubleValue()
                : technicalEngine.calculateEMA(closes, 50);

        double ema200 = indicators != null && indicators.getEma200() != null
                ? indicators.getEma200().doubleValue()
                : technicalEngine.calculateEMA(closes, 200);

        TechnicalAnalysisEngine.VWAPResult vwapResult = technicalEngine.calculateVWAP(data);

        // Determine trend bias
        double price = latest.getClose().doubleValue();
        String trendBias;
        if (price > ema20 && ema20 > ema50 && ema50 > ema200) {
            trendBias = "STRONG_UPTREND";
        } else if (price > ema50 && price > ema200) {
            trendBias = "UPTREND";
        } else if (price < ema20 && ema20 < ema50 && ema50 < ema200) {
            trendBias = "STRONG_DOWNTREND";
        } else if (price < ema50 && price < ema200) {
            trendBias = "DOWNTREND";
        } else {
            trendBias = "SIDEWAYS";
        }

        return new MarketContext(
                symbol,
                price,
                rsi,
                macdResult.macd(),
                macdResult.signal(),
                ema20,
                ema50,
                ema200,
                vwapResult.vwap(),
                indicators != null && indicators.getBbUpper() != null ? indicators.getBbUpper().doubleValue() : 0,
                indicators != null && indicators.getBbLower() != null ? indicators.getBbLower().doubleValue() : 0,
                indicators != null && indicators.getAdx() != null ? indicators.getAdx().doubleValue() : 0,
                indicators != null && indicators.getVolumeRatio() != null ? indicators.getVolumeRatio().doubleValue() : 1.0,
                technicalEngine.describeCandlePattern(pattern),
                trendBias,
                data.subList(Math.max(0, data.size() - 20), data.size())
        );
    }

    // ========== Private helper methods ==========

    private String buildMarketAnalysisPrompt(String symbol, MarketData latest,
                                              TechnicalIndicator indicators,
                                              TechnicalAnalysisEngine.CandlePattern pattern,
                                              List<Double> closes) {
        return """
                Analyze the current market conditions for %s:
                
                CURRENT PRICE DATA:
                - Symbol: %s
                - Current Price: %.2f
                - Open: %.2f, High: %.2f, Low: %.2f, Close: %.2f
                - Volume: %,d
                - Timestamp: %s
                
                TECHNICAL INDICATORS:
                - RSI (14): %.2f %s
                - MACD: %.4f, Signal: %.4f, Histogram: %.4f
                - EMA 20: %.2f, EMA 50: %.2f, EMA 200: %.2f
                - VWAP: %.2f
                - Bollinger Bands: Upper %.2f, Lower %.2f
                - ADX: %.2f %s
                - Volume Ratio vs Average: %.2fx
                
                CANDLESTICK PATTERN:
                %s
                
                Please provide:
                1. TREND ANALYSIS: Current trend direction and strength across timeframes
                2. KEY LEVELS: Critical support and resistance levels with reasoning
                3. MOMENTUM ASSESSMENT: Momentum indicators interpretation and divergences
                4. VOLUME ANALYSIS: Volume pattern analysis and implications
                5. TRADING BIAS: Clear directional bias with specific entry zone, target, and stoploss
                6. RISK FACTORS: What could invalidate this view
                7. OPTIONS STRATEGY: Specific options play with strikes and expiry if applicable
                """.formatted(
                symbol, symbol,
                latest.getClose().doubleValue(),
                latest.getOpen().doubleValue(),
                latest.getHigh().doubleValue(),
                latest.getLow().doubleValue(),
                latest.getClose().doubleValue(),
                latest.getVolume() != null ? latest.getVolume() : 0,
                latest.getTimestamp(),
                indicators != null && indicators.getRsi() != null ? indicators.getRsi().doubleValue() : 0,
                indicators != null && indicators.getRsi() != null
                        ? (indicators.getRsi().doubleValue() > 70 ? "(OVERBOUGHT)" : indicators.getRsi().doubleValue() < 30 ? "(OVERSOLD)" : "(NEUTRAL)")
                        : "",
                indicators != null && indicators.getMacd() != null ? indicators.getMacd().doubleValue() : 0,
                indicators != null && indicators.getMacdSignal() != null ? indicators.getMacdSignal().doubleValue() : 0,
                indicators != null && indicators.getMacdHist() != null ? indicators.getMacdHist().doubleValue() : 0,
                indicators != null && indicators.getEma20() != null ? indicators.getEma20().doubleValue() : 0,
                indicators != null && indicators.getEma50() != null ? indicators.getEma50().doubleValue() : 0,
                indicators != null && indicators.getEma200() != null ? indicators.getEma200().doubleValue() : 0,
                indicators != null && indicators.getVwap() != null ? indicators.getVwap().doubleValue() : 0,
                indicators != null && indicators.getBbUpper() != null ? indicators.getBbUpper().doubleValue() : 0,
                indicators != null && indicators.getBbLower() != null ? indicators.getBbLower().doubleValue() : 0,
                indicators != null && indicators.getAdx() != null ? indicators.getAdx().doubleValue() : 0,
                indicators != null && indicators.getAdx() != null
                        ? (indicators.getAdx().doubleValue() > 25 ? "(TRENDING)" : "(RANGING)")
                        : "",
                indicators != null && indicators.getVolumeRatio() != null ? indicators.getVolumeRatio().doubleValue() : 1.0,
                technicalEngine.describeCandlePattern(pattern)
        );
    }

    private String buildSignalGenerationPrompt(Instrument instrument, MarketContext ctx) {
        return """
                Generate a specific trade signal for %s (%s - %s):
                
                MARKET CONTEXT:
                - Current Price: %.2f
                - Trend: %s
                - RSI: %.2f | MACD: %.4f vs Signal %.4f
                - EMA 20/50/200: %.2f / %.2f / %.2f
                - VWAP: %.2f
                - Volume Ratio: %.2fx
                - Candle Pattern: %s
                - ADX: %.2f
                
                Generate a JSON-format trade signal with EXACT values:
                {
                  "signal_type": "BUY/SELL/STRONG_BUY/STRONG_SELL/CALL_BUY/PUT_BUY/NEUTRAL",
                  "confidence": <0-100 number>,
                  "entry_price": <exact price>,
                  "target1": <first target>,
                  "target2": <second target>,
                  "stoploss": <exact stoploss>,
                  "time_horizon": "INTRADAY/SWING_2_5_DAYS/POSITIONAL_2_4_WEEKS/INVESTMENT_3_12_MONTHS",
                  "reasoning": "<2-3 sentence reasoning with specific technical/fundamental basis>",
                  "hedging_strategy": "<specific options hedge to protect position>",
                  "strike_price": <options strike if applicable or null>,
                  "probability": <estimated success probability 0-100>
                }
                
                Base the signal on ALL available technical data. Be specific with prices.
                """.formatted(
                instrument.getSymbol(),
                instrument.getExchange().name(),
                instrument.getSegment().name(),
                ctx.currentPrice(),
                ctx.trendBias(),
                ctx.rsi(),
                ctx.macd(),
                ctx.macdSignal(),
                ctx.ema20(),
                ctx.ema50(),
                ctx.ema200(),
                ctx.vwap(),
                ctx.volumeRatio(),
                ctx.candlePattern(),
                ctx.adx()
        );
    }

    private String buildScenarioAnalysisPrompt(TradeSignal signal) {
        return """
                Provide a comprehensive 4-scenario analysis for this trade signal on %s:
                
                SIGNAL DETAILS:
                - Type: %s
                - Entry: %.2f
                - Target 1: %.2f
                - Target 2: %.2f
                - Stoploss: %.2f
                - Confidence: %.0f%%
                - Time Horizon: %s
                
                Provide 4 scenarios with probability weights (must sum to 100%%):
                
                1. BULLISH SCENARIO (what drives price to target with probability %%):
                   - Catalysts, price trajectory, exit strategy
                
                2. BEARISH SCENARIO (what triggers stoploss with probability %%):
                   - Risk factors, how to manage, whether to re-enter
                
                3. SIDEWAYS SCENARIO (consolidation with probability %%):
                   - Time-based exit, opportunity cost, how to handle
                
                4. BLACK SWAN SCENARIO (tail risk event with probability %%):
                   - Extreme events, gap scenarios, emergency exit strategy
                
                Format your response with clear BULLISH_SCENARIO:, BEARISH_SCENARIO:, SIDEWAYS_SCENARIO:, BLACK_SWAN_SCENARIO:, PROBABILITIES: labels.
                """.formatted(
                signal.getInstrument().getSymbol(),
                signal.getSignalType().name(),
                signal.getEntryPrice().doubleValue(),
                signal.getTarget1() != null ? signal.getTarget1().doubleValue() : 0,
                signal.getTarget2() != null ? signal.getTarget2().doubleValue() : 0,
                signal.getStoploss() != null ? signal.getStoploss().doubleValue() : 0,
                signal.getConfidence() != null ? signal.getConfidence().doubleValue() : 0,
                signal.getTimeHorizon() != null ? signal.getTimeHorizon() : "SWING"
        );
    }

    private TradeSignal parseSignalFromLLMResponse(Instrument instrument, MarketContext ctx, String llmResponse) {
        // Parse LLM JSON response; fall back gracefully on parse errors
        try {
            if (llmResponse != null && llmResponse.contains("\"signal_type\"")) {
                // Extract values from JSON response using basic parsing
                SignalType signalType = extractSignalType(llmResponse);
                double confidence = extractDoubleFromJson(llmResponse, "confidence", 60);
                double entryPrice = extractDoubleFromJson(llmResponse, "entry_price", ctx.currentPrice());
                double target1 = extractDoubleFromJson(llmResponse, "target1", ctx.currentPrice() * 1.05);
                double target2 = extractDoubleFromJson(llmResponse, "target2", ctx.currentPrice() * 1.10);
                double stoploss = extractDoubleFromJson(llmResponse, "stoploss", ctx.currentPrice() * 0.97);
                String timeHorizon = extractStringFromJson(llmResponse, "time_horizon", "SWING_2_5_DAYS");
                String reasoning = extractStringFromJson(llmResponse, "reasoning", "Technical setup with positive risk-reward.");
                String hedging = extractStringFromJson(llmResponse, "hedging_strategy", "Buy OTM put options as protection.");
                double probability = extractDoubleFromJson(llmResponse, "probability", 55);

                double riskReward = Math.abs(stoploss - entryPrice) > 0
                        ? Math.abs(target1 - entryPrice) / Math.abs(stoploss - entryPrice) : 2.0;

                return TradeSignal.builder()
                        .instrument(instrument)
                        .signalType(signalType)
                        .confidence(BigDecimal.valueOf(Math.min(100, Math.max(0, confidence))))
                        .entryPrice(BigDecimal.valueOf(entryPrice))
                        .target1(BigDecimal.valueOf(target1))
                        .target2(BigDecimal.valueOf(target2))
                        .stoploss(BigDecimal.valueOf(stoploss))
                        .riskReward(BigDecimal.valueOf(riskReward))
                        .timeHorizon(timeHorizon)
                        .reasoning(reasoning)
                        .hedgingStrategy(hedging)
                        .probability(BigDecimal.valueOf(probability))
                        .status(SignalStatus.ACTIVE)
                        .expiresAt(OffsetDateTime.now().plusDays(5))
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM signal response: {}", e.getMessage());
        }

        return buildDefaultSignal(instrument, ctx);
    }

    private ScenarioAnalysis parseScenarioAnalysis(String response) {
        String bullish = extractSection(response, "BULLISH_SCENARIO:", 500);
        String bearish = extractSection(response, "BEARISH_SCENARIO:", 500);
        String sideways = extractSection(response, "SIDEWAYS_SCENARIO:", 500);
        String blackSwan = extractSection(response, "BLACK_SWAN_SCENARIO:", 500);

        // Extract probabilities
        double bullProb = 40, bearProb = 30, sidewaysProb = 30;
        if (response.contains("PROBABILITIES:")) {
            String probSection = extractSection(response, "PROBABILITIES:", 200);
            bullProb = extractDoubleFromText(probSection, "bullish", 40);
            bearProb = extractDoubleFromText(probSection, "bearish", 30);
            sidewaysProb = 100 - bullProb - bearProb;
        }

        return new ScenarioAnalysis(
                bullish.isEmpty() ? "Positive catalysts drive price to targets as technical setup plays out." : bullish,
                bearish.isEmpty() ? "Negative sentiment or macro headwinds trigger stop loss." : bearish,
                sideways.isEmpty() ? "Market consolidates in a range, time-based exit recommended." : sideways,
                blackSwan.isEmpty() ? "Tail risk event causes extreme gap move. Keep position sized conservatively." : blackSwan,
                bullProb, bearProb, sidewaysProb
        );
    }

    private TradeSignal buildDefaultSignal(Instrument instrument, MarketContext ctx) {
        double price = ctx.currentPrice();
        SignalType signalType = ctx.rsi() < 35 ? SignalType.BUY
                : ctx.rsi() > 65 ? SignalType.SELL
                : SignalType.NEUTRAL;

        double target1 = signalType.isBullish() ? price * 1.05 : price * 0.95;
        double stoploss = signalType.isBullish() ? price * 0.97 : price * 1.03;

        return TradeSignal.builder()
                .instrument(instrument)
                .signalType(signalType)
                .confidence(BigDecimal.valueOf(55))
                .entryPrice(BigDecimal.valueOf(price))
                .target1(BigDecimal.valueOf(target1))
                .target2(BigDecimal.valueOf(signalType.isBullish() ? price * 1.10 : price * 0.90))
                .stoploss(BigDecimal.valueOf(stoploss))
                .riskReward(BigDecimal.valueOf(Math.abs(target1 - price) / Math.abs(stoploss - price)))
                .timeHorizon("SWING_2_5_DAYS")
                .reasoning("Technical setup based on RSI " + String.format("%.1f", ctx.rsi()) + " with trend " + ctx.trendBias())
                .hedgingStrategy("Consider OTM options for protection")
                .probability(BigDecimal.valueOf(55))
                .status(SignalStatus.ACTIVE)
                .expiresAt(OffsetDateTime.now().plusDays(5))
                .build();
    }

    private String generateFallbackAnalysis(String symbol, MarketData latest,
                                            TechnicalIndicator indicators,
                                            TechnicalAnalysisEngine.CandlePattern pattern) {
        double rsi = indicators != null && indicators.getRsi() != null ? indicators.getRsi().doubleValue() : 50;
        String rsiLevel = rsi > 70 ? "overbought" : rsi < 30 ? "oversold" : "neutral";
        String patternDesc = technicalEngine.describeCandlePattern(pattern);

        return String.format("""
                MARKET ANALYSIS for %s (Fallback - AI Model Unavailable):
                Current Price: %.2f | RSI: %.1f (%s)
                Pattern: %s
                Trend: Price is %s key moving averages.
                Recommendation: %s positioning with defined risk management.
                Note: Full AI analysis temporarily unavailable. Using rule-based analysis.
                """,
                symbol,
                latest.getClose().doubleValue(),
                rsi, rsiLevel,
                patternDesc,
                rsi > 50 ? "above" : "below",
                rsi > 70 ? "Cautious long or exit" : rsi < 30 ? "Selective buying" : "Neutral"
        );
    }

    private SignalType extractSignalType(String json) {
        for (SignalType type : SignalType.values()) {
            if (json.contains("\"" + type.name() + "\"")) {
                return type;
            }
        }
        return SignalType.NEUTRAL;
    }

    private double extractDoubleFromJson(String json, String field, double defaultVal) {
        String pattern = "\"" + field + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return defaultVal;
        String sub = json.substring(idx + pattern.length());
        int colonIdx = sub.indexOf(':');
        if (colonIdx < 0) return defaultVal;
        sub = sub.substring(colonIdx + 1).trim();
        int endIdx = sub.indexOf('\n');
        if (endIdx < 0) endIdx = sub.indexOf(',');
        if (endIdx < 0) endIdx = sub.indexOf('}');
        if (endIdx < 0) return defaultVal;
        try {
            return Double.parseDouble(sub.substring(0, endIdx).trim().replace("\"", ""));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private String extractStringFromJson(String json, String field, String defaultVal) {
        String pattern = "\"" + field + "\": \"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return defaultVal;
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return defaultVal;
        return json.substring(start, end);
    }

    private String extractSection(String text, String label, int maxLength) {
        int idx = text.indexOf(label);
        if (idx < 0) return "";
        String sub = text.substring(idx + label.length()).trim();
        // Find next label or end
        int end = sub.length();
        for (String nextLabel : List.of("BULLISH_SCENARIO:", "BEARISH_SCENARIO:", "SIDEWAYS_SCENARIO:", "BLACK_SWAN_SCENARIO:", "PROBABILITIES:")) {
            int nextIdx = sub.indexOf(nextLabel);
            if (nextIdx > 0 && nextIdx < end) end = nextIdx;
        }
        return sub.substring(0, Math.min(end, maxLength)).trim();
    }

    private double extractDoubleFromText(String text, String keyword, double defaultVal) {
        int idx = text.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) return defaultVal;
        String sub = text.substring(idx);
        int pctIdx = sub.indexOf('%');
        if (pctIdx < 0) return defaultVal;
        // Find number before %
        int start = pctIdx - 1;
        while (start > 0 && (Character.isDigit(sub.charAt(start - 1)) || sub.charAt(start - 1) == '.')) start--;
        try {
            return Double.parseDouble(sub.substring(start, pctIdx).trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private String formatPrice(BigDecimal price) {
        return price != null ? String.format("%.2f", price.doubleValue()) : "N/A";
    }
}
