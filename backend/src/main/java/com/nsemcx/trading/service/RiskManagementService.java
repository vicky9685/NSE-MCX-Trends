package com.nsemcx.trading.service;

import com.nsemcx.trading.model.TradeSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RiskManagementService {

    // Java 21 records for return types
    public record PositionSize(
            double capital,
            double riskPercent,
            double entryPrice,
            double stoploss,
            double riskAmount,
            double riskPerShare,
            int shares,
            double totalInvestment,
            double maxLoss,
            String recommendation
    ) {}

    public record RiskReward(
            double entry,
            double target,
            double stoploss,
            double riskAmount,
            double rewardAmount,
            double ratio,
            boolean isFavorable
    ) {}

    public record VaRResult(
            double portfolioValue,
            double var95,
            double var99,
            double expectedShortfall,
            double confidence,
            String interpretation
    ) {}

    public record DrawdownResult(
            double maxDrawdown,
            double currentDrawdown,
            double peakValue,
            double troughValue,
            String severity
    ) {}

    public record RiskAssessment(
            double totalPortfolioRisk,
            double concentrationRisk,
            double sectorExposure,
            VaRResult varAnalysis,
            DrawdownResult drawdownAnalysis,
            double volatilityWarningLevel,
            List<String> riskWarnings,
            String overallRiskLevel,
            boolean isWithinRiskLimits,
            double recommendedMaxPositions
    ) {}

    // India VIX proxy thresholds
    private static final double VIX_LOW = 12.0;
    private static final double VIX_MEDIUM = 18.0;
    private static final double VIX_HIGH = 24.0;
    private static final double VIX_EXTREME = 30.0;

    public PositionSize calculatePositionSize(double capital, double riskPercent,
                                              double entryPrice, double stoploss) {
        if (capital <= 0 || riskPercent <= 0 || entryPrice <= 0 || stoploss <= 0) {
            log.warn("Invalid parameters for position sizing");
            return new PositionSize(capital, riskPercent, entryPrice, stoploss,
                    0, 0, 0, 0, 0, "INVALID: Check input parameters");
        }

        double riskAmount = capital * (riskPercent / 100.0);
        double riskPerShare = Math.abs(entryPrice - stoploss);

        if (riskPerShare == 0) {
            return new PositionSize(capital, riskPercent, entryPrice, stoploss,
                    riskAmount, 0, 0, 0, 0, "INVALID: Entry and stoploss cannot be same price");
        }

        int shares = (int) Math.floor(riskAmount / riskPerShare);
        double totalInvestment = shares * entryPrice;
        double maxLoss = shares * riskPerShare;

        // Cap at 20% of capital for single position
        if (totalInvestment > capital * 0.20) {
            shares = (int) Math.floor((capital * 0.20) / entryPrice);
            totalInvestment = shares * entryPrice;
            maxLoss = shares * riskPerShare;
        }

        String recommendation = generatePositionRecommendation(shares, totalInvestment, capital,
                riskPercent, riskPerShare, entryPrice);

        return new PositionSize(capital, riskPercent, entryPrice, stoploss,
                riskAmount, riskPerShare, shares, totalInvestment, maxLoss, recommendation);
    }

    private String generatePositionRecommendation(int shares, double investment, double capital,
                                                   double riskPct, double riskPerShare, double entry) {
        if (shares == 0) {
            return "WARNING: Position too small to trade. Insufficient capital or stoploss too tight.";
        }
        double pctOfCapital = (investment / capital) * 100;
        return String.format("BUY %d shares at %.2f (%.1f%% of capital). Risk per share: %.2f. "
                        + "Max drawdown if stoploss hit: %.1f%% of capital",
                shares, entry, pctOfCapital, riskPerShare, riskPct);
    }

    public RiskReward calculateRiskReward(double entry, double target, double stoploss) {
        double riskAmount = Math.abs(entry - stoploss);
        double rewardAmount = Math.abs(target - entry);
        double ratio = riskAmount > 0 ? rewardAmount / riskAmount : 0;
        boolean favorable = ratio >= 2.0; // Minimum 2:1 RR required

        return new RiskReward(entry, target, stoploss, riskAmount, rewardAmount, ratio, favorable);
    }

    public RiskAssessment assessPortfolioRisk(List<TradeSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return new RiskAssessment(0, 0, 0,
                    new VaRResult(0, 0, 0, 0, 0.95, "No positions"),
                    new DrawdownResult(0, 0, 0, 0, "NONE"),
                    0, List.of("No active signals"), "LOW", true, 10);
        }

        // Total portfolio risk: sum of individual position risks
        double totalRisk = signals.stream()
                .mapToDouble(s -> {
                    if (s.getEntryPrice() != null && s.getStoploss() != null && s.getConfidence() != null) {
                        double positionRisk = Math.abs(s.getEntryPrice().doubleValue() - s.getStoploss().doubleValue())
                                / s.getEntryPrice().doubleValue() * 100;
                        return positionRisk * (s.getConfidence().doubleValue() / 100.0);
                    }
                    return 2.0; // default 2% risk
                })
                .sum();

        // Concentration risk: how many signals are in same instrument
        double concentrationRisk = signals.stream()
                .collect(Collectors.groupingBy(s -> s.getInstrument().getSymbol(), Collectors.counting()))
                .values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0) / (double) signals.size() * 100;

        // Sector exposure (using exchange as proxy for now)
        double sectorExposure = signals.stream()
                .collect(Collectors.groupingBy(s -> s.getInstrument().getExchange().name(), Collectors.counting()))
                .values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0) / (double) signals.size() * 100;

        // VaR calculation
        VaRResult varResult = calculateVaR(signals);

        // Drawdown analysis
        DrawdownResult drawdown = calculateDrawdown(signals);

        // Volatility warning (using signal confidence as proxy for volatility)
        double avgConfidence = signals.stream()
                .mapToDouble(s -> s.getConfidence() != null ? s.getConfidence().doubleValue() : 50)
                .average().orElse(50);
        double volatilityWarning = Math.max(0, 100 - avgConfidence); // Inverse of confidence

        List<String> warnings = generateRiskWarnings(totalRisk, concentrationRisk, sectorExposure,
                varResult, drawdown, signals.size());

        String overallRisk;
        if (totalRisk < 5) overallRisk = "LOW";
        else if (totalRisk < 10) overallRisk = "MEDIUM";
        else if (totalRisk < 20) overallRisk = "HIGH";
        else overallRisk = "EXTREME";

        boolean withinLimits = totalRisk <= 10.0 && concentrationRisk <= 50.0 && signals.size() <= 15;
        double maxPositions = Math.min(15, Math.floor(100.0 / Math.max(totalRisk / signals.size(), 1)));

        return new RiskAssessment(totalRisk, concentrationRisk, sectorExposure,
                varResult, drawdown, volatilityWarning, warnings, overallRisk, withinLimits, maxPositions);
    }

    public VaRResult calculateVaR(List<TradeSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return new VaRResult(0, 0, 0, 0, 0.95, "No portfolio");
        }

        // Calculate portfolio value and individual risk exposures
        double portfolioValue = signals.stream()
                .mapToDouble(s -> s.getEntryPrice() != null ? s.getEntryPrice().doubleValue() : 0)
                .sum();

        // Historical simulation: generate hypothetical returns based on risk levels
        List<Double> dailyReturns = signals.stream()
                .flatMap(s -> {
                    if (s.getEntryPrice() == null || s.getStoploss() == null) return List.<Double>of().stream();
                    double maxLoss = (s.getEntryPrice().doubleValue() - s.getStoploss().doubleValue())
                            / s.getEntryPrice().doubleValue();
                    // Simulate 252 daily returns using normal distribution assumption
                    double dailyVol = maxLoss / 3.0; // Assume 3-sigma event hits stoploss
                    List<Double> simReturns = new java.util.ArrayList<>();
                    for (int i = 0; i < 252; i++) {
                        // Simple parametric approach
                        simReturns.add(-dailyVol * (Math.random() * 2 - 0.5));
                    }
                    return simReturns.stream();
                })
                .sorted()
                .collect(Collectors.toList());

        double var95 = 0;
        double var99 = 0;
        double expectedShortfall = 0;

        if (!dailyReturns.isEmpty()) {
            int idx95 = (int) Math.floor(dailyReturns.size() * 0.05);
            int idx99 = (int) Math.floor(dailyReturns.size() * 0.01);

            var95 = -dailyReturns.get(Math.max(0, idx95)) * portfolioValue;
            var99 = -dailyReturns.get(Math.max(0, idx99)) * portfolioValue;

            // Expected Shortfall (CVaR): average of losses beyond VaR 95%
            int esCount = Math.max(1, idx95);
            double esSum = dailyReturns.subList(0, esCount).stream()
                    .mapToDouble(Double::doubleValue).sum();
            expectedShortfall = -esSum / esCount * portfolioValue;
        }

        String interpretation = String.format(
                "With 95%% confidence, daily portfolio loss will not exceed ₹%.0f. " +
                        "In extreme scenarios (5%% of days), expected average loss is ₹%.0f.",
                var95, expectedShortfall);

        return new VaRResult(portfolioValue, var95, var99, expectedShortfall, 0.95, interpretation);
    }

    public DrawdownResult calculateDrawdown(List<TradeSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return new DrawdownResult(0, 0, 0, 0, "NONE");
        }

        // Use signal confidence and risk levels to estimate drawdown
        double avgRisk = signals.stream()
                .mapToDouble(s -> {
                    if (s.getEntryPrice() != null && s.getStoploss() != null) {
                        return Math.abs(s.getEntryPrice().doubleValue() - s.getStoploss().doubleValue())
                                / s.getEntryPrice().doubleValue() * 100;
                    }
                    return 2.0;
                })
                .average().orElse(2.0);

        // Historical drawdown based on risk per trade and number of positions
        double maxDrawdown = avgRisk * Math.sqrt(signals.size()); // Square root of time/positions
        double currentDrawdown = maxDrawdown * 0.3; // Assume 30% of max currently
        double peakValue = 1_000_000; // Normalized
        double troughValue = peakValue * (1 - maxDrawdown / 100);

        String severity;
        if (maxDrawdown < 5) severity = "MINIMAL";
        else if (maxDrawdown < 10) severity = "LOW";
        else if (maxDrawdown < 20) severity = "MODERATE";
        else if (maxDrawdown < 30) severity = "HIGH";
        else severity = "EXTREME";

        return new DrawdownResult(maxDrawdown, currentDrawdown, peakValue, troughValue, severity);
    }

    public String assessVolatilityWarning(double vixLevel) {
        if (vixLevel < VIX_LOW) {
            return "COMPLACENCY: India VIX at extreme lows (" + vixLevel + "). Market may be underpricing risk. " +
                    "Buying protection (hedges) is cheap. Consider adding protective positions.";
        } else if (vixLevel < VIX_MEDIUM) {
            return "NORMAL: India VIX in normal range (" + vixLevel + "). Standard position sizing appropriate. " +
                    "Market is pricing risk efficiently.";
        } else if (vixLevel < VIX_HIGH) {
            return "ELEVATED: India VIX elevated (" + vixLevel + "). Reduce position sizes by 25-30%. " +
                    "Use tighter stop losses. Avoid naked options selling.";
        } else if (vixLevel < VIX_EXTREME) {
            return "HIGH: India VIX high (" + vixLevel + "). Reduce position sizes by 50%. " +
                    "Focus on quality setups only. Avoid leverage. Consider staying in cash.";
        } else {
            return "EXTREME: India VIX extremely high (" + vixLevel + "). Market in crisis mode. " +
                    "Preserve capital. Exit leveraged positions. Consider going to cash or hedging entire portfolio.";
        }
    }

    private List<String> generateRiskWarnings(double totalRisk, double concentrationRisk,
                                              double sectorExposure, VaRResult var,
                                              DrawdownResult drawdown, int signalCount) {
        List<String> warnings = new ArrayList<>();

        if (totalRisk > 10) {
            warnings.add("CRITICAL: Total portfolio risk (" + String.format("%.1f%%", totalRisk) +
                    ") exceeds 10% maximum. Reduce position sizes immediately.");
        }

        if (concentrationRisk > 50) {
            warnings.add("WARNING: High concentration risk (" + String.format("%.0f%%", concentrationRisk) +
                    ") in single instrument. Diversify across at least 5-8 instruments.");
        }

        if (sectorExposure > 60) {
            warnings.add("WARNING: Sector exposure (" + String.format("%.0f%%", sectorExposure) +
                    ") too concentrated. Spread across NSE equities and MCX commodities.");
        }

        if (signalCount > 15) {
            warnings.add("WARNING: Too many active signals (" + signalCount +
                    "). Quality over quantity - focus on top 5-8 high-conviction setups.");
        }

        if ("EXTREME".equals(drawdown.severity())) {
            warnings.add("CRITICAL: Maximum drawdown estimate (" +
                    String.format("%.1f%%", drawdown.maxDrawdown()) +
                    ") is extremely high. Review risk management immediately.");
        }

        if (warnings.isEmpty()) {
            warnings.add("Portfolio risk is within acceptable parameters. Continue monitoring.");
        }

        return Collections.unmodifiableList(warnings);
    }
}
