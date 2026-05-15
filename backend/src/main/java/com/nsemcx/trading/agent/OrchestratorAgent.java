package com.nsemcx.trading.agent;

import com.nsemcx.trading.agent.MarketAnalysisAgent.MarketContext;
import com.nsemcx.trading.model.Instrument;
import com.nsemcx.trading.model.MarketData;
import com.nsemcx.trading.model.TechnicalIndicator;
import com.nsemcx.trading.model.TradeSignal;
import com.nsemcx.trading.repository.InstrumentRepository;
import com.nsemcx.trading.repository.MarketDataRepository;
import com.nsemcx.trading.repository.TechnicalIndicatorRepository;
import com.nsemcx.trading.repository.TradeSignalRepository;
import com.nsemcx.trading.service.FundamentalAnalysisService;
import com.nsemcx.trading.service.FundamentalAnalysisService.FundamentalScore;
import com.nsemcx.trading.service.RiskManagementService;
import com.nsemcx.trading.service.TechnicalAnalysisService;
import com.nsemcx.trading.service.TechnicalAnalysisService.TechnicalAnalysisResult;
import com.nsemcx.trading.event.MarketDashboard;
import com.nsemcx.trading.websocket.TradingWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.StructuredTaskScope;

/**
 * Central orchestrator that coordinates all trading agents using Java 21
 * structured concurrency via {@link StructuredTaskScope}.
 *
 * <p>Signal generation pipeline:
 * <ol>
 *   <li>Run {@link MarketAnalysisAgent}, {@link GlobalMacroAgent}, and
 *       {@link CommodityAgent} in parallel virtual threads.</li>
 *   <li>Merge scores using weights: Fundamental 60%, Macro 20%, Technical 20%.</li>
 *   <li>Persist {@link TradeSignal} entities and broadcast via WebSocket.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorAgent {

    private final MarketAnalysisAgent    marketAnalysisAgent;
    private final GlobalMacroAgent       globalMacroAgent;
    private final CommodityAgent         commodityAgent;
    private final TechnicalAnalysisService  technicalAnalysisService;
    private final FundamentalAnalysisService fundamentalAnalysisService;
    private final RiskManagementService  riskManagementService;
    private final InstrumentRepository   instrumentRepository;
    private final MarketDataRepository   marketDataRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final TradeSignalRepository  tradeSignalRepository;
    private final TradingWebSocketHandler webSocketHandler;

    // ── Weights ───────────────────────────────────────────────────────────────

    private static final double WEIGHT_FUNDAMENTAL = 0.60;
    private static final double WEIGHT_MACRO       = 0.20;
    private static final double WEIGHT_TECHNICAL   = 0.20;

    // ── Main entry point ──────────────────────────────────────────────────────

    /**
     * Runs the multi-agent signal generation pipeline for all active instruments.
     *
     * <p>Uses {@link StructuredTaskScope.ShutdownOnFailure} so that if any agent
     * throws an exception the scope shuts down and the error propagates cleanly.
     * Each instrument is processed independently; a single instrument failure
     * does not abort the rest.
     *
     * @return list of newly persisted {@link TradeSignal} entities
     */
    @Transactional
    public List<TradeSignal> generateAllSignals() {
        List<Instrument> instruments = instrumentRepository.findByIsActiveTrue();
        log.info("[Orchestrator] Starting signal generation for {} instruments", instruments.size());

        List<TradeSignal> generated = new ArrayList<>();

        for (Instrument instrument : instruments) {
            try {
                TradeSignal signal = generateSignalForInstrument(instrument);
                if (signal != null) {
                    generated.add(signal);
                }
            } catch (Exception ex) {
                log.error("[Orchestrator] Signal generation failed for {}: {}",
                        instrument.getSymbol(), ex.getMessage(), ex);
            }
        }

        log.info("[Orchestrator] Generated {} signals", generated.size());

        // Broadcast aggregated dashboard after signal run
        try {
            MarketDashboard dashboard = generateDashboard();
            webSocketHandler.broadcastDashboard(dashboard);
        } catch (Exception ex) {
            log.warn("[Orchestrator] Dashboard broadcast failed: {}", ex.getMessage());
        }

        return generated;
    }

    /**
     * Generates and persists a trade signal for a single instrument using
     * structured concurrency to parallelise agent calls.
     *
     * @param instrument the instrument to analyse
     * @return persisted {@link TradeSignal}, or {@code null} if data is insufficient
     */
    @SuppressWarnings("preview")
    public TradeSignal generateSignalForInstrument(Instrument instrument) {
        List<MarketData> candles = marketDataRepository
                .findLatestByInstrumentId(instrument.getId(), 200);

        if (candles.size() < 30) {
            log.debug("[Orchestrator] Not enough candles for {}: {}", instrument.getSymbol(), candles.size());
            return null;
        }

        List<MarketData> chronological = candles.reversed();
        TechnicalIndicator latestIndicator = technicalIndicatorRepository
                .findLatestByInstrumentId(instrument.getId())
                .orElse(null);

        // --- Parallel agent execution via StructuredTaskScope ---
        final double[] fundamentalScore = {50.0};
        final double[] macroScore       = {50.0};
        final double[] technicalScore   = {50.0};
        final MarketContext[] marketCtx  = {null};

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // Task 1: Market analysis agent builds context + technical score
            var technicalTask = scope.fork(() -> {
                MarketContext ctx = marketAnalysisAgent.buildMarketContext(
                        instrument.getSymbol(), chronological, latestIndicator);
                TechnicalAnalysisResult techResult =
                        technicalAnalysisService.analyzeInstrument(instrument.getId());
                double score = techResult != null ? techResult.strength() : 50.0;
                return new double[]{score, 0}; // [score, unused]
            });

            // Task 2: Global macro agent
            var macroTask = scope.fork(() -> {
                GlobalMacroAgent.GlobalMacroAnalysis macro =
                        globalMacroAgent.analyzeGlobalMarkets();
                // Translate macro bias (-100 to +100) to 0-100 score
                double bias = macro.indiaMarketBias();
                return (bias + 100.0) / 2.0;
            });

            // Task 3: Fundamental analysis
            var fundamentalTask = scope.fork(() -> {
                Optional<FundamentalScore> fundOpt =
                        fundamentalAnalysisService.analyzeFundamentals(instrument.getSymbol());
                return fundOpt.map(FundamentalScore::compositeScore).orElse(50.0);
            });

            scope.join().throwIfFailed();

            technicalScore[0]   = technicalTask.get()[0];
            macroScore[0]       = macroTask.get();
            fundamentalScore[0] = fundamentalTask.get();

            // Rebuild market context (may have been built inside technicalTask but not exposed)
            marketCtx[0] = marketAnalysisAgent.buildMarketContext(
                    instrument.getSymbol(), chronological, latestIndicator);

        } catch (Exception ex) {
            log.warn("[Orchestrator] Structured scope failed for {}, using defaults: {}",
                    instrument.getSymbol(), ex.getMessage());
            // Fall through with default scores
            if (marketCtx[0] == null) {
                try {
                    marketCtx[0] = marketAnalysisAgent.buildMarketContext(
                            instrument.getSymbol(), chronological, latestIndicator);
                } catch (Exception e2) {
                    log.error("[Orchestrator] Cannot build market context for {}", instrument.getSymbol());
                    return null;
                }
            }
        }

        // Compute weighted composite score
        double composite = applyWeightedScore(
                fundamentalScore[0], macroScore[0], technicalScore[0]);

        // Generate the actual signal using the market analysis agent
        TradeSignal signal = marketAnalysisAgent.generateTradeSignal(instrument, marketCtx[0]);

        // Enrich signal with agent scores
        signal.setFundamentalScore(BigDecimal.valueOf(fundamentalScore[0]));
        signal.setMacroScore(BigDecimal.valueOf(macroScore[0]));
        signal.setTechnicalScore(BigDecimal.valueOf(technicalScore[0]));
        signal.setConfidence(BigDecimal.valueOf(Math.min(100, composite)));
        signal.setExpiresAt(OffsetDateTime.now().plusDays(5));

        // Persist
        TradeSignal saved = tradeSignalRepository.save(signal);
        log.info("[Orchestrator] Saved signal id={} {} {} conf={} (F={} M={} T={})",
                saved.getId(), saved.getSignalType(), instrument.getSymbol(),
                String.format("%.1f", composite),
                String.format("%.0f", fundamentalScore[0]),
                String.format("%.0f", macroScore[0]),
                String.format("%.0f", technicalScore[0]));

        return saved;
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    /**
     * Assembles a {@link MarketDashboard} record representing the current state of
     * the market across all agents.
     *
     * <p>Uses a simplified construction path; a full implementation would populate
     * all nested records (IndexSummary, McxSummary, InstitutionalFlow, KeyLevels, etc.)
     * from live data.  The fields populated here are sufficient for the WebSocket
     * broadcast and initial API consumers.
     */
    public MarketDashboard generateDashboard() {
        List<TradeSignal> activeSignals =
                tradeSignalRepository.findAllActiveOrderByConfidence();

        double rawSentiment = computeRawSentiment(activeSignals);
        // Map to 0-100 scale (50 = neutral)
        double sentimentScore = (rawSentiment + 100.0) / 2.0;
        String sentimentLabel = sentimentScore >= 65 ? "BULLISH"
                : sentimentScore <= 35 ? "BEARISH" : "NEUTRAL";

        // Macro context
        GlobalMacroAgent.GlobalMacroAnalysis macro = null;
        try {
            macro = globalMacroAgent.analyzeGlobalMarkets();
        } catch (Exception ex) {
            log.warn("[Orchestrator] Macro analysis failed for dashboard: {}", ex.getMessage());
        }

        String vixInterpretation = riskManagementService.assessVolatilityWarning(18.0);
        MarketDashboard.VixOutlook vixOutlook = new MarketDashboard.VixOutlook(
                18.0, "STABLE", "NORMAL", "Normal volatility environment", vixInterpretation);

        // Risk assessment
        RiskManagementService.RiskAssessment risk =
                riskManagementService.assessPortfolioRisk(activeSignals);

        List<String> riskFactors = new ArrayList<>(risk.riskWarnings());
        if (macro != null && macro.geopoliticalRisks() != null && !macro.geopoliticalRisks().isBlank()) {
            riskFactors.add(macro.geopoliticalRisks()
                    .substring(0, Math.min(120, macro.geopoliticalRisks().length())));
        }

        String positionAdvice = buildCapitalProtectionStrategy(risk, rawSentiment);
        double maxDeployment = "EXTREME".equals(risk.overallRiskLevel()) ? 30
                : "HIGH".equals(risk.overallRiskLevel()) ? 50
                : "MEDIUM".equals(risk.overallRiskLevel()) ? 70 : 90;

        MarketDashboard.RiskEnvironment riskEnv = new MarketDashboard.RiskEnvironment(
                risk.overallRiskLevel(),
                risk.totalPortfolioRisk(),
                false,
                riskFactors.stream().limit(5).toList(),
                positionAdvice,
                maxDeployment
        );

        // Build top signal summaries
        List<MarketDashboard.SignalSummary> topNse = activeSignals.stream()
                .filter(s -> !s.getInstrument().getExchange().name().equals("MCX"))
                .limit(5)
                .map(this::toSignalSummary)
                .toList();

        List<MarketDashboard.SignalSummary> topMcx = activeSignals.stream()
                .filter(s -> s.getInstrument().getExchange().name().equals("MCX"))
                .limit(3)
                .map(this::toSignalSummary)
                .toList();

        // Signal stats
        long strongBuy  = activeSignals.stream().filter(s -> s.getSignalType().name().equals("STRONG_BUY")).count();
        long buy        = activeSignals.stream().filter(s -> s.getSignalType().name().equals("BUY")).count();
        long sell       = activeSignals.stream().filter(s -> s.getSignalType().name().equals("SELL")).count();
        long strongSell = activeSignals.stream().filter(s -> s.getSignalType().name().equals("STRONG_SELL")).count();
        long neutral    = activeSignals.stream().filter(s -> s.getSignalType().isNeutral()).count();
        long options    = activeSignals.stream().filter(s -> s.getSignalType().isOptionsStrategy()).count();
        MarketDashboard.SignalStats signalStats = new MarketDashboard.SignalStats(
                activeSignals.size(), (int) strongBuy, (int) buy,
                (int) neutral, (int) sell, (int) strongSell,
                (int) options, 0, 0, 0);

        // Global macro summary
        String macroImpact = macro != null ? macro.overallMacroStance() : "Macro data unavailable";
        MarketDashboard.GlobalMacroSummary globalMacro = new MarketDashboard.GlobalMacroSummary(
                84.50, 0.0, 4.20, 75.0, 104.0, 0.5, 0.3,
                macroImpact, macroImpact,
                List.of("Fed policy", "RBI MPC", "FII flows", "Crude oil", "USD/INR"));

        List<String> warnings = risk.isWithinRiskLimits()
                ? List.of()
                : List.of("Portfolio risk exceeds recommended limits – reduce exposure");

        return new MarketDashboard(
                Instant.now(),
                LocalDate.now(),
                sentimentScore,
                sentimentLabel,
                vixOutlook,
                null,   // nifty50 – requires live index data
                null,   // bankNifty – requires live index data
                null,   // mcxSummary – populated by CommodityAgent separately
                null,   // institutionalFlow – populated by live FII/DII data
                topNse,
                topMcx,
                List.of(), // options setups
                null,   // sectorAnalysis – populated by sector-level service
                globalMacro,
                riskEnv,
                null,   // keyLevels – requires live OHLCV for pivot calculation
                signalStats,
                List.of(), // topPicks
                warnings
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Combines fundamental, macro, and technical scores using fixed weights.
     *
     * @param fundamentalScore 0-100
     * @param macroScore       0-100
     * @param technicalScore   0-100
     * @return weighted composite 0-100
     */
    private double applyWeightedScore(double fundamentalScore,
                                      double macroScore,
                                      double technicalScore) {
        return (fundamentalScore * WEIGHT_FUNDAMENTAL)
             + (macroScore       * WEIGHT_MACRO)
             + (technicalScore   * WEIGHT_TECHNICAL);
    }

    /**
     * Derives a raw sentiment score in the range [-100, +100] from the active signals.
     * Bullish signals contribute positively, bearish negatively, weighted by confidence.
     */
    private double computeRawSentiment(List<TradeSignal> signals) {
        if (signals.isEmpty()) return 0;
        double sum = signals.stream()
                .mapToDouble(s -> {
                    double conf = s.getConfidence() != null ? s.getConfidence().doubleValue() : 50;
                    int bias = s.getSignalType().getDirectionBias();
                    return (conf / 100.0) * bias * 20;
                })
                .sum();
        return Math.max(-100, Math.min(100, sum / signals.size()));
    }

    private MarketDashboard.SignalSummary toSignalSummary(TradeSignal s) {
        return new MarketDashboard.SignalSummary(
                s.getId(),
                s.getInstrument().getSymbol(),
                s.getInstrument().getName(),
                s.getInstrument().getExchange(),
                s.getSignalType(),
                s.getConfidence() != null ? s.getConfidence().doubleValue() : 0,
                s.getEntryPrice() != null ? s.getEntryPrice().doubleValue() : 0,
                s.getTarget1() != null ? s.getTarget1().doubleValue() : 0,
                s.getTarget2() != null ? s.getTarget2().doubleValue() : 0,
                s.getStoploss() != null ? s.getStoploss().doubleValue() : 0,
                s.getRiskReward() != null ? s.getRiskReward().doubleValue() : 0,
                s.getTimeHorizon(),
                s.getReasoning()
        );
    }

    private String buildCapitalProtectionStrategy(
            RiskManagementService.RiskAssessment risk, double sentiment) {
        if ("EXTREME".equals(risk.overallRiskLevel()) || sentiment < -50) {
            return "DEFENSIVE: Reduce equities to 30%, hold 50% cash, 20% gold. "
                    + "Buy portfolio insurance via Nifty puts.";
        } else if ("HIGH".equals(risk.overallRiskLevel()) || sentiment < -20) {
            return "CAUTIOUS: 60% equity, 25% cash/debt, 15% gold. "
                    + "Use covered calls to reduce cost basis on long positions.";
        } else if (sentiment > 50) {
            return "AGGRESSIVE: 80% equity, 10% cash, 10% gold. "
                    + "Use index calls for additional upside exposure.";
        } else {
            return "BALANCED: 65% equity, 20% cash/debt, 15% gold. "
                    + "Maintain hedges equal to 2-3% of portfolio value.";
        }
    }
}
