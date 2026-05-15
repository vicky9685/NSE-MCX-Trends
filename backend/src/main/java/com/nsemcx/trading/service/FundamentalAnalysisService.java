package com.nsemcx.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nsemcx.trading.model.FundamentalData;
import com.nsemcx.trading.model.Instrument;
import com.nsemcx.trading.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundamentalAnalysisService {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final InstrumentRepository instrumentRepository;

    @Value("${yahoo.finance.v10-url:https://query1.finance.yahoo.com/v10/finance/quoteSummary}")
    private String yahooV10Url;

    // Java 21 record for fundamental score
    public record FundamentalScore(
            String symbol,
            double revenueGrowthScore,
            double profitGrowthScore,
            double debtEquityScore,
            double roeScore,
            double roeceScore,
            double promoterHoldingScore,
            double peScore,
            double pbScore,
            double evEbitdaScore,
            double fiiDiiFlowScore,
            double compositeScore,
            String grade,
            FundamentalData fundamentalData
    ) {
        public boolean isExcellent() { return compositeScore >= 80; }
        public boolean isGood() { return compositeScore >= 60; }
        public boolean isPoor() { return compositeScore < 40; }

        public static String gradeFromScore(double score) {
            if (score >= 85) return "A+";
            if (score >= 75) return "A";
            if (score >= 65) return "B+";
            if (score >= 55) return "B";
            if (score >= 45) return "C";
            if (score >= 35) return "D";
            return "F";
        }
    }

    @Transactional
    public Optional<FundamentalScore> analyzeFundamentals(String symbol) {
        log.info("Analyzing fundamentals for symbol: {}", symbol);

        Optional<Instrument> instrumentOpt = instrumentRepository.findBySymbol(symbol);
        if (instrumentOpt.isEmpty()) {
            log.warn("Instrument not found: {}", symbol);
            return Optional.empty();
        }

        Instrument instrument = instrumentOpt.get();
        String yahooSymbol = instrument.getYahooSymbol();

        try {
            String modules = "defaultKeyStatistics,financialData,summaryDetail,earningsTrend,majorHoldersBreakdown";
            String url = String.format("%s/%s?modules=%s", yahooV10Url, yahooSymbol, modules);

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (compatible; NSE-MCX-Trading/1.0)")
                    .header("Accept", "application/json")
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Failed to fetch fundamentals for {}: HTTP {}", symbol, response.code());
                    return Optional.empty();
                }

                String body = response.body().string();
                return parseFundamentals(instrument, body);
            }

        } catch (IOException e) {
            log.error("IO error fetching fundamentals for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error analyzing fundamentals for {}: {}", symbol, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<FundamentalScore> parseFundamentals(Instrument instrument, String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode quoteSummary = root.path("quoteSummary");
        JsonNode result = quoteSummary.path("result");

        if (!result.isArray() || result.isEmpty()) {
            return Optional.empty();
        }

        JsonNode data = result.get(0);
        JsonNode keyStats = data.path("defaultKeyStatistics");
        JsonNode financialData = data.path("financialData");
        JsonNode summaryDetail = data.path("summaryDetail");
        JsonNode majorHolders = data.path("majorHoldersBreakdown");

        // Extract metrics safely
        double revenueGrowth = getDoubleValue(financialData, "revenueGrowth");
        double earningsGrowth = getDoubleValue(financialData, "earningsGrowth");
        double debtToEquity = getDoubleValue(financialData, "debtToEquity");
        double returnOnEquity = getDoubleValue(financialData, "returnOnEquity");
        double returnOnAssets = getDoubleValue(financialData, "returnOnAssets");
        double trailingPE = getDoubleValue(summaryDetail, "trailingPE");
        double priceToBook = getDoubleValue(keyStats, "priceToBook");
        double enterpriseToEbitda = getDoubleValue(keyStats, "enterpriseToEbitda");
        double dividendYield = getDoubleValue(summaryDetail, "dividendYield");
        double marketCap = getDoubleValue(summaryDetail, "marketCap");
        double bookValue = getDoubleValue(keyStats, "bookValue");
        double trailingEps = getDoubleValue(keyStats, "trailingEps");

        // Promoter holding from majorHolders
        double insiderHolding = getDoubleValue(majorHolders, "insidersPercentHeld");
        double institutionHolding = getDoubleValue(majorHolders, "institutionsPercentHeld");

        // ROCE approximation = returnOnAssets * (1 + debtToEquity) is a rough proxy
        double roce = returnOnAssets > 0 ? returnOnAssets * (1 + Math.max(debtToEquity, 0)) : 0;

        FundamentalData fundData = FundamentalData.builder()
                .instrument(instrument)
                .revenueGrowth(bd(revenueGrowth * 100))
                .profitGrowth(bd(earningsGrowth * 100))
                .debtEquity(bd(debtToEquity / 100))  // Yahoo returns in percentage
                .roe(bd(returnOnEquity * 100))
                .roce(bd(roce * 100))
                .promoterHolding(bd(insiderHolding * 100))
                .peRatio(bd(trailingPE))
                .pbRatio(bd(priceToBook))
                .evEbitda(bd(enterpriseToEbitda))
                .marketCap(bd(marketCap))
                .dividendYield(bd(dividendYield * 100))
                .eps(bd(trailingEps))
                .bookValue(bd(bookValue))
                .build();

        // Score each metric on 0-100 scale
        double revGrowthScore = scoreRevenueGrowth(revenueGrowth * 100);
        double profitGrowthScore = scoreProfitGrowth(earningsGrowth * 100);
        double debtScore = scoreDebtEquity(debtToEquity / 100);
        double roeScore = scoreROE(returnOnEquity * 100);
        double roceScore = scoreROCE(roce * 100);
        double promoterScore = scorePromoterHolding(insiderHolding * 100);
        double peScore = scorePE(trailingPE);
        double pbScore = scorePB(priceToBook);
        double evEbitdaScore = scoreEVEBITDA(enterpriseToEbitda);
        double fiiDiiScore = 50.0; // Default neutral - would need actual FII/DII flow data

        // Weighted composite: earnings quality most important
        double composite = (revGrowthScore * 0.10)
                + (profitGrowthScore * 0.15)
                + (debtScore * 0.10)
                + (roeScore * 0.15)
                + (roceScore * 0.15)
                + (promoterScore * 0.10)
                + (peScore * 0.10)
                + (pbScore * 0.05)
                + (evEbitdaScore * 0.05)
                + (fiiDiiScore * 0.05);

        FundamentalScore score = new FundamentalScore(
                instrument.getSymbol(),
                revGrowthScore,
                profitGrowthScore,
                debtScore,
                roeScore,
                roceScore,
                promoterScore,
                peScore,
                pbScore,
                evEbitdaScore,
                fiiDiiScore,
                Math.min(100, Math.max(0, composite)),
                FundamentalScore.gradeFromScore(composite),
                fundData
        );

        return Optional.of(score);
    }

    // ========== Individual Scoring Functions (0-100) ==========

    private double scoreRevenueGrowth(double growthPct) {
        // >20% = 100, 10-20% = 75, 5-10% = 50, 0-5% = 25, negative = 0
        if (growthPct >= 20) return 100;
        if (growthPct >= 10) return 75 + (growthPct - 10) * 2.5;
        if (growthPct >= 5) return 50 + (growthPct - 5) * 5;
        if (growthPct >= 0) return growthPct * 5;
        return Math.max(0, 25 + growthPct * 2); // penalty for negative
    }

    private double scoreProfitGrowth(double growthPct) {
        if (growthPct >= 25) return 100;
        if (growthPct >= 15) return 80 + (growthPct - 15) * 2;
        if (growthPct >= 5) return 60 + (growthPct - 5) * 2;
        if (growthPct >= 0) return growthPct * 12;
        return Math.max(0, 30 + growthPct * 2);
    }

    private double scoreDebtEquity(double de) {
        // Lower is better for most industries
        if (de < 0) return 80; // Cash rich (negative debt)
        if (de == 0) return 100;
        if (de <= 0.5) return 90;
        if (de <= 1.0) return 70;
        if (de <= 2.0) return 50;
        if (de <= 3.0) return 30;
        return Math.max(0, 30 - (de - 3) * 10);
    }

    private double scoreROE(double roePct) {
        // >20% excellent
        if (roePct >= 25) return 100;
        if (roePct >= 20) return 90;
        if (roePct >= 15) return 75;
        if (roePct >= 10) return 60;
        if (roePct >= 5) return 40;
        if (roePct >= 0) return 20;
        return 0;
    }

    private double scoreROCE(double rocePct) {
        if (rocePct >= 25) return 100;
        if (rocePct >= 20) return 85;
        if (rocePct >= 15) return 70;
        if (rocePct >= 10) return 55;
        if (rocePct >= 5) return 35;
        return Math.max(0, rocePct * 7);
    }

    private double scorePromoterHolding(double holdingPct) {
        // 50-75% is ideal range - too high can mean low float, too low means low confidence
        if (holdingPct >= 50 && holdingPct <= 75) return 100;
        if (holdingPct >= 40 && holdingPct < 50) return 80;
        if (holdingPct > 75 && holdingPct <= 85) return 75;
        if (holdingPct >= 30 && holdingPct < 40) return 60;
        if (holdingPct > 85) return 60; // Very high holding may limit liquidity
        if (holdingPct >= 20) return 40;
        return Math.max(0, holdingPct * 2);
    }

    private double scorePE(double pe) {
        // Context-dependent, but generally: 10-20x = sweet spot for India large caps
        if (pe <= 0) return 0; // Loss-making
        if (pe >= 5 && pe <= 15) return 100;
        if (pe > 15 && pe <= 25) return 85;
        if (pe > 25 && pe <= 35) return 65;
        if (pe > 35 && pe <= 50) return 45;
        if (pe > 50) return Math.max(0, 45 - (pe - 50) * 0.5);
        return 50; // Very low PE might indicate value trap
    }

    private double scorePB(double pb) {
        if (pb <= 0) return 0;
        if (pb >= 1 && pb <= 3) return 100;
        if (pb > 3 && pb <= 5) return 75;
        if (pb > 5 && pb <= 8) return 55;
        if (pb > 8) return Math.max(0, 55 - (pb - 8) * 3);
        return 60; // PB < 1 could be undervalued but also risky
    }

    private double scoreEVEBITDA(double evEbitda) {
        if (evEbitda <= 0) return 0;
        if (evEbitda >= 8 && evEbitda <= 15) return 100;
        if (evEbitda > 15 && evEbitda <= 20) return 75;
        if (evEbitda > 20 && evEbitda <= 30) return 50;
        if (evEbitda > 30) return Math.max(0, 50 - (evEbitda - 30));
        return 80; // Low EV/EBITDA generally attractive
    }

    // ========== Utility ==========

    private double getDoubleValue(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) return 0;
        // Yahoo Finance wraps values in {raw: value, fmt: "formatted"} objects
        JsonNode rawNode = fieldNode.path("raw");
        if (!rawNode.isMissingNode() && !rawNode.isNull()) {
            return rawNode.asDouble(0);
        }
        return fieldNode.asDouble(0);
    }

    private BigDecimal bd(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return null;
        return BigDecimal.valueOf(value);
    }
}
