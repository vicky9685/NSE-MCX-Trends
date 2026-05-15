package com.nsemcx.trading.service;

import com.nsemcx.trading.indicators.TechnicalAnalysisEngine;
import com.nsemcx.trading.indicators.TechnicalAnalysisEngine.ADXResult;
import com.nsemcx.trading.indicators.TechnicalAnalysisEngine.BBResult;
import com.nsemcx.trading.indicators.TechnicalAnalysisEngine.CandlePattern;
import com.nsemcx.trading.indicators.TechnicalAnalysisEngine.MACDResult;
import com.nsemcx.trading.indicators.TechnicalAnalysisEngine.StochasticResult;
import com.nsemcx.trading.indicators.TechnicalAnalysisEngine.VWAPResult;
import com.nsemcx.trading.model.Instrument;
import com.nsemcx.trading.model.MarketData;
import com.nsemcx.trading.model.TechnicalIndicator;
import com.nsemcx.trading.repository.InstrumentRepository;
import com.nsemcx.trading.repository.MarketDataRepository;
import com.nsemcx.trading.repository.TechnicalIndicatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates the {@link TechnicalAnalysisEngine} to compute all technical
 * indicators for a given instrument, persists the results to
 * {@link TechnicalIndicatorRepository}, and returns a rich
 * {@link TechnicalAnalysisResult} record.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechnicalAnalysisService {

    private static final int CANDLES_REQUIRED = 200;

    private final TechnicalAnalysisEngine engine;
    private final MarketDataRepository marketDataRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final InstrumentRepository instrumentRepository;

    // ── Result record ─────────────────────────────────────────────────────────

    /**
     * Immutable result of a full technical analysis pass on one instrument.
     *
     * @param instrument        the analysed instrument
     * @param latestIndicators  the persisted {@link TechnicalIndicator} entity
     * @param candlePattern     recognised candlestick pattern (sealed interface)
     * @param trend             overall trend direction
     * @param strength          composite trend strength 0-100
     * @param signals           human-readable list of actionable signal strings
     */
    public record TechnicalAnalysisResult(
            Instrument instrument,
            TechnicalIndicator latestIndicators,
            CandlePattern candlePattern,
            Trend trend,
            int strength,
            List<String> signals
    ) {}

    /** Direction of the overall price trend. */
    public enum Trend { BULLISH, BEARISH, SIDEWAYS }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Performs a full technical analysis pass for a single instrument.
     *
     * <p>Fetches up to 200 candles, computes all indicators via
     * {@link TechnicalAnalysisEngine}, persists the result, and returns a
     * {@link TechnicalAnalysisResult} record.
     *
     * @param instrumentId database primary key of the instrument
     * @return analysis result, or {@code null} if insufficient data
     */
    @Transactional
    public TechnicalAnalysisResult analyzeInstrument(Long instrumentId) {
        List<MarketData> candles =
                marketDataRepository.findLatestByInstrumentId(instrumentId, CANDLES_REQUIRED);

        if (candles.size() < 30) {
            log.warn("Insufficient candles for instrument id={}: have {}, need >=30",
                    instrumentId, candles.size());
            return null;
        }

        // Engine works with chronological order (oldest first)
        List<MarketData> chronological = candles.reversed();
        Instrument instrument = chronological.get(0).getInstrument();

        List<Double> closes = engine.extractClosePrices(chronological);

        // Compute indicators
        double rsi         = engine.calculateRSI(closes, 14);
        MACDResult macd    = engine.calculateMACD(closes);
        double ema20       = engine.calculateEMA(closes, 20);
        double ema50       = engine.calculateEMA(closes, 50);
        double ema200      = engine.calculateEMA(closes, Math.min(200, closes.size() - 1));
        VWAPResult vwap    = engine.calculateVWAP(chronological);
        BBResult bb        = engine.calculateBollingerBands(closes, 20, 2.0);
        ADXResult adx      = engine.calculateADX(chronological, 14);
        double atr         = engine.calculateATR(chronological, 14);
        StochasticResult stoch = engine.calculateStochastic(chronological, 14, 3);
        double volRatio    = engine.calculateVolumeRatio(chronological, 20);
        CandlePattern pattern = engine.identifyCandlePattern(chronological);

        MarketData latest = chronological.get(chronological.size() - 1);
        OffsetDateTime timestamp = latest.getTimestamp();

        // Build and save TechnicalIndicator entity
        TechnicalIndicator indicator = TechnicalIndicator.builder()
                .instrument(instrument)
                .timestamp(timestamp)
                .rsi(bd(rsi))
                .macd(bd(macd.macd()))
                .macdSignal(bd(macd.signal()))
                .macdHist(bd(macd.histogram()))
                .ema20(bd(ema20))
                .ema50(bd(ema50))
                .ema200(bd(ema200))
                .vwap(bd(vwap.vwap()))
                .bbUpper(bd(bb.upper()))
                .bbMiddle(bd(bb.middle()))
                .bbLower(bd(bb.lower()))
                .volumeRatio(bd(volRatio))
                .adx(bd(adx.adx()))
                .atr(bd(atr))
                .stochK(bd(stoch.k()))
                .stochD(bd(stoch.d()))
                .build();

        // Upsert: skip if an indicator already exists for this instrument + timestamp
        if (!technicalIndicatorRepository.existsByInstrumentIdAndTimestamp(
                instrumentId, timestamp)) {
            indicator = technicalIndicatorRepository.save(indicator);
        } else {
            indicator = technicalIndicatorRepository
                    .findLatestByInstrumentId(instrumentId)
                    .orElse(indicator);
        }

        double price = latest.getClose().doubleValue();
        Trend trend = determineTrend(price, ema20, ema50, ema200);
        int strength = computeStrength(rsi, adx.adx(), macd, bb, price, ema20, ema50, ema200);
        List<String> signals = buildSignalMessages(
                indicator, pattern, trend, strength, price, adx, macd, vwap);

        log.info("Technical analysis for {} (id={}): trend={} strength={} pattern={}",
                instrument.getSymbol(), instrumentId, trend, strength, pattern.name());

        return new TechnicalAnalysisResult(instrument, indicator, pattern, trend, strength, signals);
    }

    /**
     * Non-blocking entry point used by the Kafka consumer to trigger analysis
     * without blocking the consumer thread.
     */
    @Async("tradingExecutor")
    public CompletableFuture<TechnicalAnalysisResult> analyzeInstrumentAsync(Long instrumentId) {
        try {
            return CompletableFuture.completedFuture(analyzeInstrument(instrumentId));
        } catch (Exception ex) {
            log.error("Async technical analysis failed for instrument id={}: {}",
                    instrumentId, ex.getMessage(), ex);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Runs full technical analysis on every active instrument using one virtual
     * thread per instrument via {@link CompletableFuture}.
     */
    public List<TechnicalAnalysisResult> analyzeAllActive() {
        List<Instrument> instruments = instrumentRepository.findByIsActiveTrue();
        log.info("Running technical analysis for {} active instruments", instruments.size());

        // Use a per-task virtual thread executor so each instrument gets its own virtual thread
        java.util.concurrent.ExecutorService vtExecutor =
                java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<TechnicalAnalysisResult>> futures = instruments.stream()
                .map(inst -> CompletableFuture.supplyAsync(
                        () -> analyzeInstrumentSafe(inst.getId()), vtExecutor))
                .toList();

        List<TechnicalAnalysisResult> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(r -> r != null)
                .toList();

        vtExecutor.shutdown();
        return results;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private TechnicalAnalysisResult analyzeInstrumentSafe(Long id) {
        try {
            return analyzeInstrument(id);
        } catch (Exception ex) {
            log.error("Technical analysis error for instrument id={}: {}", id, ex.getMessage(), ex);
            return null;
        }
    }

    private Trend determineTrend(double price, double ema20, double ema50, double ema200) {
        boolean aboveAll = price > ema20 && ema20 > ema50 && ema50 > ema200;
        boolean belowAll = price < ema20 && ema20 < ema50 && ema50 < ema200;
        boolean aboveMajor = price > ema50 && price > ema200;
        boolean belowMajor = price < ema50 && price < ema200;

        if (aboveAll || aboveMajor) return Trend.BULLISH;
        if (belowAll || belowMajor) return Trend.BEARISH;
        return Trend.SIDEWAYS;
    }

    /**
     * Composite strength 0-100 derived from RSI zone, ADX, MACD, and EMA alignment.
     */
    private int computeStrength(double rsi, double adx, MACDResult macd,
                                BBResult bb, double price,
                                double ema20, double ema50, double ema200) {
        int score = 0;

        // RSI contribution (0-25)
        if (rsi > 60 || rsi < 40) score += 25;
        else if (rsi > 55 || rsi < 45) score += 15;
        else score += 5;

        // ADX contribution (0-25): trending market
        if (adx > 40) score += 25;
        else if (adx > 25) score += 18;
        else if (adx > 15) score += 8;

        // MACD contribution (0-25)
        if (macd.isBullishCrossover() || macd.isBearishCrossover()) score += 25;
        else if (Math.abs(macd.histogram()) > 0.5) score += 12;
        else score += 5;

        // EMA alignment contribution (0-25)
        boolean allAligned = (price > ema20 && ema20 > ema50 && ema50 > ema200)
                || (price < ema20 && ema20 < ema50 && ema50 < ema200);
        boolean twoAligned = (price > ema50 && price > ema200)
                || (price < ema50 && price < ema200);
        if (allAligned) score += 25;
        else if (twoAligned) score += 15;
        else score += 5;

        return Math.min(100, score);
    }

    private List<String> buildSignalMessages(
            TechnicalIndicator ti,
            CandlePattern pattern,
            Trend trend,
            int strength,
            double price,
            ADXResult adx,
            MACDResult macd,
            VWAPResult vwap) {

        List<String> signals = new ArrayList<>();

        // Trend
        signals.add("Trend: " + trend.name() + " (strength " + strength + "/100)");

        // RSI
        if (ti.isRsiOversold()) {
            signals.add("RSI " + format(ti.getRsi()) + " – OVERSOLD: watch for bullish reversal entry");
        } else if (ti.isRsiOverbought()) {
            signals.add("RSI " + format(ti.getRsi()) + " – OVERBOUGHT: consider profit booking or reduce size");
        } else {
            signals.add("RSI " + format(ti.getRsi()) + " – neutral zone");
        }

        // MACD
        if (macd.isBullishCrossover()) {
            signals.add("MACD bullish crossover: MACD " + f4(macd.macd()) + " > Signal " + f4(macd.signal()));
        } else if (macd.isBearishCrossover()) {
            signals.add("MACD bearish crossover: MACD " + f4(macd.macd()) + " < Signal " + f4(macd.signal()));
        }

        // ADX trend strength
        if (adx.isBullishTrend()) {
            signals.add("ADX " + f2(adx.adx()) + " – strong BULLISH trend: +DI=" + f2(adx.diPlus()) + " > -DI=" + f2(adx.diMinus()));
        } else if (adx.isBearishTrend()) {
            signals.add("ADX " + f2(adx.adx()) + " – strong BEARISH trend: -DI=" + f2(adx.diMinus()) + " > +DI=" + f2(adx.diPlus()));
        } else if (!adx.isTrending()) {
            signals.add("ADX " + f2(adx.adx()) + " – ranging market: avoid breakout strategies");
        }

        // VWAP
        if (price > vwap.vwap()) {
            signals.add("Price " + f2(price) + " above VWAP " + f2(vwap.vwap()) + " – institutional buy bias");
        } else {
            signals.add("Price " + f2(price) + " below VWAP " + f2(vwap.vwap()) + " – institutional sell bias");
        }

        // Candle pattern
        signals.add("Candle pattern: " + engine.describeCandlePattern(pattern));

        // Stochastic
        if (ti.getStochK() != null) {
            double k = ti.getStochK().doubleValue();
            if (k < 20) signals.add("Stochastic %K=" + f2(k) + " – OVERSOLD: potential reversal zone");
            else if (k > 80) signals.add("Stochastic %K=" + f2(k) + " – OVERBOUGHT: consider exits");
        }

        return List.copyOf(signals);
    }

    private BigDecimal bd(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return null;
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private String format(BigDecimal v) { return v != null ? String.format("%.2f", v.doubleValue()) : "N/A"; }
    private String f2(double v)  { return String.format("%.2f", v); }
    private String f4(double v)  { return String.format("%.4f", v); }
}
