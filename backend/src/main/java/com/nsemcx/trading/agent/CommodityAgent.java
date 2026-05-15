package com.nsemcx.trading.agent;

import com.nsemcx.trading.model.MarketData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommodityAgent {

    private final ChatClient chatClient;

    // Java 21 record for commodity signals
    public record CommoditySignal(
            String commodity,
            String mcxSymbol,
            String globalSymbol,
            double currentPrice,
            double target1,
            double target2,
            double stoploss,
            String signalType,         // BUY/SELL/NEUTRAL
            double confidence,
            String fundamentalDrivers,
            String technicalSetup,
            String globalContext,
            String seasonalFactors,
            String hedgingStrategy,
            String optionsStrategy,
            String riskFactors,
            double riskReward,
            String timeHorizon,
            double supportLevel,
            double resistanceLevel
    ) {
        public boolean isBullish() { return "BUY".equals(signalType) || "STRONG_BUY".equals(signalType); }
        public boolean isBearish() { return "SELL".equals(signalType) || "STRONG_SELL".equals(signalType); }
    }

    private static final String COMMODITY_SYSTEM_PROMPT = """
            You are the world's top commodity trading expert and analyst with 30+ years specializing in:
            - MCX (Multi Commodity Exchange) listed commodities: Gold, Silver, Crude Oil, Natural Gas, Copper
            - Global commodity markets: COMEX Gold/Silver, NYMEX Crude/NatGas, LME Copper
            - Commodity-specific fundamental drivers unique to each market
            - MCX-specific factors: INR conversion, MCX lot sizes, Indian seasonal demand patterns
            - Options strategies on MCX: CE/PE strategies specific to commodity options
            
            For each commodity, you understand:
            GOLD: Central bank buying, real yields, DXY, geopolitical uncertainty, Indian wedding season demand, ETF flows
            SILVER: Gold-silver ratio, industrial demand (solar, EVs), solar panel manufacturing
            CRUDE OIL: OPEC+ decisions, US inventory data, geopolitical supply disruptions, demand destruction
            NATURAL GAS: Weather seasonality, LNG exports, storage levels, Henry Hub dynamics, India's dependence
            COPPER: China PMI, EV revolution demand, mine supply disruptions, global growth proxy
            
            Always provide:
            - MCX price levels (convert international prices at current USD/INR)
            - Specific entry levels, targets, and stop losses on MCX
            - Seasonal patterns for Indian market timing
            - Relevant upcoming events that could move prices
            """;

    public CommoditySignal analyzeGold(MarketAnalysisAgent.MarketContext ctx) {
        String prompt = buildGoldPrompt(ctx);
        return analyzeCommodity("GOLD", "MCX:GOLD", "GC=F", prompt, ctx.currentPrice());
    }

    public CommoditySignal analyzeSilver(MarketAnalysisAgent.MarketContext ctx) {
        String prompt = buildSilverPrompt(ctx);
        return analyzeCommodity("SILVER", "MCX:SILVER", "SI=F", prompt, ctx.currentPrice());
    }

    public CommoditySignal analyzeCrudeOil(MarketAnalysisAgent.MarketContext ctx) {
        String prompt = buildCrudeOilPrompt(ctx);
        return analyzeCommodity("CRUDEOIL", "MCX:CRUDEOIL", "CL=F", prompt, ctx.currentPrice());
    }

    public CommoditySignal analyzeNaturalGas(MarketAnalysisAgent.MarketContext ctx) {
        String prompt = buildNaturalGasPrompt(ctx);
        return analyzeCommodity("NATURALGAS", "MCX:NATURALGAS", "NG=F", prompt, ctx.currentPrice());
    }

    public CommoditySignal analyzeCopper(MarketAnalysisAgent.MarketContext ctx) {
        String prompt = buildCopperPrompt(ctx);
        return analyzeCommodity("COPPER", "MCX:COPPER", "HG=F", prompt, ctx.currentPrice());
    }

    private CommoditySignal analyzeCommodity(String name, String mcxSymbol, String globalSymbol,
                                              String prompt, double currentPrice) {
        try {
            String response = chatClient.prompt()
                    .system(COMMODITY_SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();

            return parseCommoditySignal(name, mcxSymbol, globalSymbol, currentPrice, response);

        } catch (Exception e) {
            log.error("Error analyzing {}: {}", name, e.getMessage());
            return buildFallbackSignal(name, mcxSymbol, globalSymbol, currentPrice);
        }
    }

    private String buildGoldPrompt(MarketAnalysisAgent.MarketContext ctx) {
        return """
                Analyze GOLD (MCX Gold Futures) comprehensively:
                
                CURRENT DATA:
                - MCX Gold Price: %.2f INR/10g
                - RSI: %.2f | MACD: %.4f | ADX: %.2f
                - EMA20: %.2f | EMA50: %.2f | EMA200: %.2f
                - Volume Ratio: %.2fx | Trend: %s
                - Pattern: %s
                
                FUNDAMENTAL ANALYSIS REQUIRED:
                1. REAL YIELDS: US 10Y TIPS yield impact on gold (inverse correlation)
                2. DXY CORRELATION: Dollar index level and gold's response
                3. CENTRAL BANK BUYING: India RBI, China PBoC, Turkey gold purchases trend
                4. GEOPOLITICAL RISK PREMIUM: Current crisis premium in gold price
                5. COMEX POSITIONING: COT data - Managed Money long positioning
                6. ETF FLOWS: GLD, iShares Gold ETF daily flows
                7. INDIA SPECIFIC: Akshaya Tritiya, Dhanteras demand, wedding season impact
                8. TECHNICAL ANALYSIS: Key COMEX support ($) and MCX support (INR levels)
                
                PROVIDE:
                - MCX entry level, Target 1, Target 2, Stoploss (in INR)
                - Signal Type and Confidence (0-100)
                - Key events to watch next 2 weeks
                - Recommended lot size management
                - Options strategy: specific MCX Gold CE/PE recommendation with strikes
                """.formatted(
                ctx.currentPrice(), ctx.rsi(), ctx.macd(), ctx.adx(),
                ctx.ema20(), ctx.ema50(), ctx.ema200(),
                ctx.volumeRatio(), ctx.trendBias(), ctx.candlePattern()
        );
    }

    private String buildSilverPrompt(MarketAnalysisAgent.MarketContext ctx) {
        return """
                Analyze SILVER (MCX Silver Futures) comprehensively:
                
                CURRENT DATA:
                - MCX Silver Price: %.2f INR/kg
                - RSI: %.2f | MACD: %.4f | ADX: %.2f
                - Trend: %s | Pattern: %s
                
                FUNDAMENTAL ANALYSIS:
                1. GOLD-SILVER RATIO: Current ratio vs historical average (60-80x). Cheap/expensive?
                2. INDUSTRIAL DEMAND: Solar panel manufacturing (silver paste), EV batteries, 5G infrastructure
                3. INDIA DEMAND: Industrial imports, jewelry demand (lower price point than gold)
                4. COMEX SILVER: Open interest, warehouse stock levels, delivery dynamics
                5. SOLAR ENERGY NEXUS: India solar capacity addition plans and silver requirement
                6. SUPPLY: Mining production from Mexico, Peru, China
                7. INVESTMENT DEMAND: SLV ETF flows, institutional positioning
                
                PROVIDE:
                - MCX entry, Target 1 (short-term), Target 2 (medium-term), Stoploss
                - Gold-silver ratio trade if applicable (long silver/short gold)
                - Seasonal pattern analysis
                - Options strategy on MCX Silver
                """.formatted(
                ctx.currentPrice(), ctx.rsi(), ctx.macd(), ctx.adx(),
                ctx.trendBias(), ctx.candlePattern()
        );
    }

    private String buildCrudeOilPrompt(MarketAnalysisAgent.MarketContext ctx) {
        return """
                Analyze CRUDE OIL (MCX Crude Oil Futures) comprehensively:
                
                CURRENT DATA:
                - MCX Crude Oil Price: %.2f INR/barrel
                - RSI: %.2f | MACD: %.4f | ADX: %.2f
                - Trend: %s | Pattern: %s
                - Volume Ratio: %.2fx
                
                FUNDAMENTAL ANALYSIS:
                1. OPEC+ DYNAMICS: Current production quotas, compliance levels, Saudi Arabia voluntary cuts
                2. US INVENTORY DATA: EIA weekly data - Crude, Gasoline, Distillate inventory levels
                3. GEOPOLITICAL SUPPLY RISK: Iran, Russia, Libya, Venezuela supply disruptions
                4. DEMAND PICTURE: China import volumes, India's growing demand, US driving season
                5. US SHALE PRODUCTION: Baker Hughes rig count, breakeven oil prices for shale
                6. REFINERY MARGINS: Crack spreads for Asia vs Atlantic basin
                7. INDIA CONTEXT: India's import dependency (85%%+), refinery run rates (IOC/BPCL/HPCL)
                8. SEASONAL PATTERNS: Summer driving season, winter heating demand
                
                PROVIDE:
                - MCX Crude entry, T1, T2, Stoploss (in INR per barrel)
                - Brent-WTI spread implications for MCX pricing
                - India macro impact: OMC subsidy burden, current account impact
                - Risk events: EIA data, OPEC+ meetings, Middle East developments
                """.formatted(
                ctx.currentPrice(), ctx.rsi(), ctx.macd(), ctx.adx(),
                ctx.trendBias(), ctx.candlePattern(), ctx.volumeRatio()
        );
    }

    private String buildNaturalGasPrompt(MarketAnalysisAgent.MarketContext ctx) {
        return """
                Analyze NATURAL GAS (MCX Natural Gas Futures) comprehensively:
                
                CURRENT DATA:
                - MCX Natural Gas Price: %.2f INR/MMBtu
                - RSI: %.2f | MACD: %.4f | Trend: %s
                
                FUNDAMENTAL ANALYSIS:
                1. WEATHER IMPACT: Temperature forecasts for US, Europe - heating/cooling demand
                2. US STORAGE: EIA weekly storage data vs 5-year average surplus/deficit
                3. LNG EXPORTS: US LNG export capacity and actual exports (Sabine Pass, Freeport LNG)
                4. PRODUCTION LEVELS: Associated gas from Permian Basin, Appalachian production
                5. HENRY HUB TECHNICALS: COMEX price levels, seasonality patterns
                6. INDIA SPECIFICS: India's gas import dependency, GAIL, Petronet LNG stocks correlation
                7. SEASONAL PATTERNS: Natural gas is highly seasonal - identify current seasonal position
                8. POWER SECTOR DEMAND: Gas-fired power generation vs coal switching
                
                PROVIDE:
                - MCX Natural Gas entry, Target 1, Target 2, Stoploss
                - Seasonality commentary (which season are we in?)
                - High-probability direction for next 30 days
                - India gas stocks to consider as equity play
                """.formatted(
                ctx.currentPrice(), ctx.rsi(), ctx.macd(), ctx.adx(), ctx.trendBias()
        );
    }

    private String buildCopperPrompt(MarketAnalysisAgent.MarketContext ctx) {
        return """
                Analyze COPPER (MCX Copper Futures) comprehensively:
                
                CURRENT DATA:
                - MCX Copper Price: %.2f INR/kg
                - RSI: %.2f | MACD: %.4f | ADX: %.2f
                - Trend: %s | Pattern: %s
                
                FUNDAMENTAL ANALYSIS:
                1. CHINA DEMAND: China accounts for ~55%% of global copper demand. PMI data, property sector health
                2. EV REVOLUTION: Each EV uses 80-100kg copper vs 20kg for ICE vehicle. Global EV sales trajectory
                3. GRID INFRASTRUCTURE: US/EU grid modernization, renewable energy transition copper demand
                4. MINE SUPPLY: Chile/Peru production, Escondida mine output, labor strike risks
                5. LME WAREHOUSE STOCKS: Inventory levels as supply/demand balance indicator
                6. COMEX POSITIONING: Speculative positioning in copper futures
                7. GLOBAL GROWTH PROXY: Copper as leading economic indicator
                8. INDIA USAGE: India's infrastructure capex, housing sector, electrical equipment demand
                
                PROVIDE:
                - MCX entry, Target 1 (2-4 weeks), Target 2 (1-3 months), Stoploss
                - LME vs MCX spread commentary
                - Global growth implications of copper price direction
                - Correlation with Nifty Metal index
                """.formatted(
                ctx.currentPrice(), ctx.rsi(), ctx.macd(), ctx.adx(),
                ctx.trendBias(), ctx.candlePattern()
        );
    }

    private CommoditySignal parseCommoditySignal(String name, String mcxSymbol, String globalSymbol,
                                                  double currentPrice, String response) {
        if (response == null || response.isEmpty()) {
            return buildFallbackSignal(name, mcxSymbol, globalSymbol, currentPrice);
        }

        // Parse signal type from response
        String signalType = "NEUTRAL";
        if (response.toLowerCase().contains("strong buy") || response.toLowerCase().contains("strongly bullish")) {
            signalType = "STRONG_BUY";
        } else if (response.toLowerCase().contains("buy") && !response.toLowerCase().contains("sell")) {
            signalType = "BUY";
        } else if (response.toLowerCase().contains("strong sell") || response.toLowerCase().contains("strongly bearish")) {
            signalType = "STRONG_SELL";
        } else if (response.toLowerCase().contains("sell") && !response.toLowerCase().contains("buy")) {
            signalType = "SELL";
        }

        // Extract confidence from response
        double confidence = 60;
        if (response.contains("confidence:") || response.contains("Confidence:")) {
            confidence = extractDouble(response, "onfidence", 60);
        } else if ("STRONG_BUY".equals(signalType) || "STRONG_SELL".equals(signalType)) {
            confidence = 75;
        }

        double target1 = currentPrice * (signalType.contains("BUY") ? 1.05 : 0.95);
        double target2 = currentPrice * (signalType.contains("BUY") ? 1.10 : 0.90);
        double stoploss = currentPrice * (signalType.contains("BUY") ? 0.97 : 1.03);
        double rr = Math.abs(target1 - currentPrice) / Math.abs(stoploss - currentPrice);

        return new CommoditySignal(
                name, mcxSymbol, globalSymbol, currentPrice,
                target1, target2, stoploss,
                signalType, confidence,
                extractSection(response, "FUNDAMENTAL", "TECHNICAL"),
                extractSection(response, "TECHNICAL", "GLOBAL"),
                extractSection(response, "global", "SEASONAL"),
                extractSection(response, "SEASONAL", "PROVIDE"),
                "Buy OTM puts equal to 1% of position value as hedge",
                extractSection(response, "Options strategy", ""),
                extractSection(response, "Risk", ""),
                rr, "SWING_2_5_DAYS",
                stoploss * 0.99, target1 * 1.01
        );
    }

    private CommoditySignal buildFallbackSignal(String name, String mcxSymbol, String globalSymbol, double price) {
        return new CommoditySignal(
                name, mcxSymbol, globalSymbol, price,
                price * 1.03, price * 1.06, price * 0.98,
                "NEUTRAL", 50,
                "Fundamental data unavailable - using technical bias only",
                "Price at key moving averages. Neutral bias until direction confirmed.",
                "Global context analysis pending AI model availability",
                "No seasonal bias identified at this time",
                "Protective options recommended given uncertainty",
                "Wait for clearer setup before adding options position",
                "AI model unavailable for full risk assessment",
                1.5, "SWING_2_5_DAYS",
                price * 0.97, price * 1.03
        );
    }

    private String extractSection(String text, String start, String end) {
        if (text == null || start == null) return "";
        int startIdx = text.toLowerCase().indexOf(start.toLowerCase());
        if (startIdx < 0) return "";
        startIdx = text.indexOf('\n', startIdx);
        if (startIdx < 0) return "";
        int endIdx = end != null && !end.isEmpty()
                ? text.toLowerCase().indexOf(end.toLowerCase(), startIdx)
                : -1;
        if (endIdx < 0) endIdx = Math.min(text.length(), startIdx + 400);
        return text.substring(startIdx, endIdx).trim();
    }

    private double extractDouble(String text, String keyword, double defaultVal) {
        int idx = text.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) return defaultVal;
        String sub = text.substring(idx);
        // Find first number after keyword
        int start = -1;
        for (int i = keyword.length(); i < Math.min(50, sub.length()); i++) {
            char c = sub.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                if (start < 0) start = i;
            } else if (start >= 0) {
                try {
                    return Double.parseDouble(sub.substring(start, i));
                } catch (NumberFormatException e) {
                    return defaultVal;
                }
            }
        }
        return defaultVal;
    }
}
