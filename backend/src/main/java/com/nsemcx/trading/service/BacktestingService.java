package com.nsemcx.trading.service;

import com.nsemcx.trading.config.TradingProperties;
import com.nsemcx.trading.domain.SignalType;
import com.nsemcx.trading.event.BacktestProgress;
import com.nsemcx.trading.indicators.TechnicalAnalysisEngine;
import com.nsemcx.trading.model.*;
import com.nsemcx.trading.model.BacktestResult.BacktestStatus;
import com.nsemcx.trading.model.BacktestTrade.ExitReason;
import com.nsemcx.trading.model.BacktestTrade.TradeDirection;
import com.nsemcx.trading.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestingService {

    private final BacktestResultRepository backtestResultRepository;
    private final BacktestTradeRepository backtestTradeRepository;
    private final MarketDataRepository marketDataRepository;
    private final InstrumentRepository instrumentRepository;
    private final TechnicalAnalysisEngine technicalEngine;
    private final TradingProperties tradingProperties;
    private final SimpMessagingTemplate messagingTemplate;

    // ── Strategy infrastructure ────────────────────────────────────────────────

    /**
     * Contract every trading strategy must implement.
     */
    public interface TradingStrategy {
        String getName();
        List<TradeSignal> generateSignals(List<MarketData> data, TechnicalIndicator indicators);
        StrategyParameters getDefaultParameters();
    }

    /**
     * Generic parameters container passed to each strategy.
     */
    public record StrategyParameters(
            double rsiOversold,
            double rsiOverbought,
            int emaPeriodFast,
            int emaPeriodSlow,
            double bbStdDev,
            double stopLossPercent,
            double takeProfitPercent,
            Map<String, Double> extra
    ) {
        public static StrategyParameters defaults() {
            return new StrategyParameters(30, 70, 20, 50, 2.0, 2.0, 4.0, Map.of());
        }
    }

    /**
     * Lightweight signal produced inside the backtesting loop.
     */
    public record TradeSignal(
            SignalType type,
            LocalDate date,
            double entryPrice,
            double targetPrice,
            double stopLoss,
            String reason
    ) {
        public boolean isBullish() { return type != null && type.isBullish(); }
        public boolean isBearish() { return type != null && type.isBearish(); }
    }

    // ── Concrete strategies ────────────────────────────────────────────────────

    @Component
    static class EmaCrossoverStrategy implements TradingStrategy {
        @Override
        public String getName() { return "EMA_CROSSOVER"; }

        @Override
        public List<TradeSignal> generateSignals(List<MarketData> data, TechnicalIndicator indicators) {
            if (indicators == null || indicators.getEma20() == null || indicators.getEma50() == null) {
                return List.of();
            }
            double ema20 = indicators.getEma20().doubleValue();
            double ema50 = indicators.getEma50().doubleValue();
            double close = data.getLast().getClose().doubleValue();

            List<TradeSignal> signals = new ArrayList<>();
            if (ema20 > ema50 * 1.001) {
                signals.add(new TradeSignal(SignalType.BUY,
                        data.getLast().getTimestamp().toLocalDate(),
                        close, close * 1.04, close * 0.98, "EMA20 crossed above EMA50"));
            } else if (ema20 < ema50 * 0.999) {
                signals.add(new TradeSignal(SignalType.SELL,
                        data.getLast().getTimestamp().toLocalDate(),
                        close, close * 0.96, close * 1.02, "EMA20 crossed below EMA50"));
            }
            return signals;
        }

        @Override
        public StrategyParameters getDefaultParameters() {
            return new StrategyParameters(30, 70, 20, 50, 2.0, 2.0, 4.0, Map.of());
        }
    }

    @Component
    static class RSIMeanReversionStrategy implements TradingStrategy {
        @Override
        public String getName() { return "RSI_MEAN_REVERSION"; }

        @Override
        public List<TradeSignal> generateSignals(List<MarketData> data, TechnicalIndicator indicators) {
            if (indicators == null || indicators.getRsi() == null) return List.of();
            double rsi = indicators.getRsi().doubleValue();
            double close = data.getLast().getClose().doubleValue();
            List<TradeSignal> signals = new ArrayList<>();
            if (rsi < 30) {
                signals.add(new TradeSignal(SignalType.BUY,
                        data.getLast().getTimestamp().toLocalDate(),
                        close, close * 1.05, close * 0.97, "RSI oversold: " + String.format("%.1f", rsi)));
            } else if (rsi > 70) {
                signals.add(new TradeSignal(SignalType.SELL,
                        data.getLast().getTimestamp().toLocalDate(),
                        close, close * 0.95, close * 1.03, "RSI overbought: " + String.format("%.1f", rsi)));
            }
            return signals;
        }

        @Override
        public StrategyParameters getDefaultParameters() {
            return new StrategyParameters(30, 70, 14, 14, 2.0, 3.0, 5.0, Map.of());
        }
    }

    @Component
    static class MACDMomentumStrategy implements TradingStrategy {
        @Override
        public String getName() { return "MACD_MOMENTUM"; }

        @Override
        public List<TradeSignal> generateSignals(List<MarketData> data, TechnicalIndicator indicators) {
            if (indicators == null || indicators.getMacd() == null || indicators.getMacdSignal() == null) {
                return List.of();
            }
            double macd = indicators.getMacd().doubleValue();
            double macdSignal = indicators.getMacdSignal().doubleValue();
            double close = data.getLast().getClose().doubleValue();
            List<TradeSignal> signals = new ArrayList<>();
            if (macd > macdSignal && macd > 0) {
                signals.add(new TradeSignal(SignalType.BUY,
                        data.getLast().getTimestamp().toLocalDate(),
                        close, close * 1.04, close * 0.98, "MACD bullish cross above signal"));
            } else if (macd < macdSignal && macd < 0) {
                signals.add(new TradeSignal(SignalType.SELL,
                        data.getLast().getTimestamp().toLocalDate(),
                        close, close * 0.96, close * 1.02, "MACD bearish cross below signal"));
            }
            return signals;
        }

        @Override
        public StrategyParameters getDefaultParameters() {
            return new StrategyParameters(30, 70, 12, 26, 2.0, 2.0, 4.0, Map.of("macdSlowPeriod", 26.0));
        }
    }

    @Component
    static class BollingerBandStrategy implements TradingStrategy {
        @Override
        public String getName() { return "BOLLINGER_BAND"; }

        @Override
        public List<TradeSignal> generateSignals(List<MarketData> data, TechnicalIndicator indicators) {
            if (indicators == null || indicators.getBbUpper() == null || indicators.getBbLower() == null) {
                return List.of();
            }
            double upper = indicators.getBbUpper().doubleValue();
            double lower = indicators.getBbLower().doubleValue();
            double close = data.getLast().getClose().doubleValue();
            double bandwidth = (upper - lower) / indicators.getBbMiddle().doubleValue();
            List<TradeSignal> signals = new ArrayList<>();
            if (close < lower && bandwidth > 0.02) {
                signals.add(new TradeSignal(SignalType.BUY,
                        data.getLast().getTimestamp().toLocalDate(),
                        close, indicators.getBbMiddle().doubleValue(), lower * 0.98,
                        "Price touched lower BB, mean reversion expected"));
            } else if (close > upper && bandwidth > 0.02) {
                signals.add(new TradeSignal(SignalType.SELL,
                        data.getLast().getTimestamp().toLocalDate(),
                        close, indicators.getBbMiddle().doubleValue(), upper * 1.02,
                        "Price touched upper BB, mean reversion expected"));
            }
            return signals;
        }

        @Override
        public StrategyParameters getDefaultParameters() {
            return new StrategyParameters(30, 70, 20, 20, 2.0, 2.0, 4.0, Map.of());
        }
    }

    @Component
    static class VWAPStrategy implements TradingStrategy {
        @Override
        public String getName() { return "VWAP"; }

        @Override
        public List<TradeSignal> generateSignals(List<MarketData> data, TechnicalIndicator indicators) {
            if (indicators == null || indicators.getVwap() == null) return List.of();
            double vwap = indicators.getVwap().doubleValue();
            double close = data.getLast().getClose().doubleValue();
            double deviation = Math.abs(close - vwap) / vwap;
            List<TradeSignal> signals = new ArrayList<>();
            if (close < vwap * 0.995 && deviation > 0.005) {
                signals.add(new TradeSignal(SignalType.BUY,
                        data.getLast().getTimestamp().toLocalDate(),
                        close, vwap, close * 0.98, "Price below VWAP, reversion long"));
            } else if (close > vwap * 1.005 && deviation > 0.005) {
                signals.add(new TradeSignal(SignalType.SELL,
                        data.getLast().getTimestamp().toLocalDate(),
                        close, vwap, close * 1.02, "Price above VWAP, reversion short"));
            }
            return signals;
        }

        @Override
        public StrategyParameters getDefaultParameters() {
            return new StrategyParameters(30, 70, 20, 50, 2.0, 2.0, 3.0, Map.of());
        }
    }

    // ── Request record ─────────────────────────────────────────────────────────

    public record BacktestRequest(
            Long instrumentId,
            String strategyName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal initialCapital,
            String parametersJson
    ) {}

    // ── Strategy registry ──────────────────────────────────────────────────────

    private final List<TradingStrategy> strategies;  // injected by Spring via constructor

    private TradingStrategy resolveStrategy(String name) {
        return strategies.stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown strategy: " + name));
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    @Transactional
    public BacktestResult runBacktest(BacktestRequest request) {
        Instrument instrument = instrumentRepository.findById(request.instrumentId())
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + request.instrumentId()));

        TradingStrategy strategy = resolveStrategy(request.strategyName());
        TradingProperties.Backtesting cfg = tradingProperties.backtesting();

        BigDecimal initialCapital = request.initialCapital() != null
                ? request.initialCapital()
                : BigDecimal.valueOf(cfg.defaultCapital());

        // Persist a PENDING result first so we have an ID for progress events
        BacktestResult result = BacktestResult.builder()
                .name(instrument.getSymbol() + " - " + strategy.getName())
                .strategyName(strategy.getName())
                .instrument(instrument)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .initialCapital(initialCapital)
                .parametersJson(request.parametersJson())
                .status(BacktestStatus.PENDING)
                .build();
        result = backtestResultRepository.save(result);

        try {
            result.setStatus(BacktestStatus.RUNNING);
            backtestResultRepository.save(result);
            publishProgress(BacktestProgress.started(result.getId(), result.getName(),
                    strategy.getName(), request.startDate(), request.endDate(), 0));

            result = executeBacktest(result, instrument, strategy, cfg, initialCapital);
        } catch (Exception ex) {
            log.error("Backtest {} failed: {}", result.getId(), ex.getMessage(), ex);
            result.setStatus(BacktestStatus.FAILED);
            backtestResultRepository.save(result);
            publishProgress(BacktestProgress.failed(result.getId(), result.getName(),
                    strategy.getName(), request.startDate(), request.endDate(), ex.getMessage()));
        }
        return result;
    }

    @Async("tradingExecutor")
    public void runBacktestAsync(BacktestRequest request) {
        runBacktest(request);
    }

    public List<BacktestResult> getBacktestResults(Long instrumentId) {
        return instrumentId != null
                ? backtestResultRepository.findByInstrumentIdOrderByCreatedAtDesc(instrumentId)
                : backtestResultRepository.findAll();
    }

    public Map<String, BacktestResult> compareStrategies(List<String> strategyNames,
                                                          Long instrumentId,
                                                          LocalDate start,
                                                          LocalDate end) {
        Map<String, BacktestResult> results = new LinkedHashMap<>();
        BigDecimal capital = BigDecimal.valueOf(tradingProperties.backtesting().defaultCapital());
        for (String name : strategyNames) {
            BacktestRequest req = new BacktestRequest(instrumentId, name, start, end, capital, null);
            results.put(name, runBacktest(req));
        }
        return results;
    }

    public List<String> availableStrategyNames() {
        return strategies.stream().map(TradingStrategy::getName).sorted().toList();
    }

    // ── Core simulation engine ─────────────────────────────────────────────────

    @Transactional
    protected BacktestResult executeBacktest(BacktestResult result, Instrument instrument,
                                              TradingStrategy strategy,
                                              TradingProperties.Backtesting cfg,
                                              BigDecimal initialCapital) {
        OffsetDateTime startDt = result.getStartDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDt = result.getEndDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<MarketData> allData = marketDataRepository
                .findByInstrumentIdAndDateRange(instrument.getId(), startDt, endDt);

        if (allData.isEmpty()) {
            throw new IllegalStateException("No market data found for " + instrument.getSymbol()
                    + " in range " + result.getStartDate() + " to " + result.getEndDate());
        }

        double capital = initialCapital.doubleValue();
        double peakCapital = capital;
        double maxDrawdown = 0.0;
        List<double[]> dailyReturns = new ArrayList<>();
        List<BacktestTrade> trades = new ArrayList<>();
        BacktestTrade openTrade = null;

        double commissionPct = cfg.commissionPercent() / 100.0;
        double slippagePct = cfg.slippagePercent() / 100.0;
        double totalCommission = 0.0;
        double totalSlippage = 0.0;

        int totalDays = allData.size();
        int daysProcessed = 0;

        for (int i = 20; i < allData.size(); i++) {
            MarketData bar = allData.get(i);
            List<MarketData> window = allData.subList(Math.max(0, i - 200), i + 1);

            // Check if open trade should be closed
            if (openTrade != null) {
                double high = bar.getHigh().doubleValue();
                double low = bar.getLow().doubleValue();
                double exitPrice = 0;
                ExitReason exitReason = null;

                if (openTrade.isLong()) {
                    if (low <= openTrade.getStoploss().doubleValue()) {
                        exitPrice = openTrade.getStoploss().doubleValue();
                        exitReason = ExitReason.SL_HIT;
                    } else if (high >= openTrade.getExitPrice().doubleValue()) {
                        // exitPrice used as target storage
                        exitPrice = openTrade.getExitPrice().doubleValue();
                        exitReason = ExitReason.TARGET_HIT;
                    }
                } else {
                    if (high >= openTrade.getStoploss().doubleValue()) {
                        exitPrice = openTrade.getStoploss().doubleValue();
                        exitReason = ExitReason.SL_HIT;
                    } else if (low <= openTrade.getExitPrice().doubleValue()) {
                        exitPrice = openTrade.getExitPrice().doubleValue();
                        exitReason = ExitReason.TARGET_HIT;
                    }
                }

                if (exitReason != null) {
                    double slipAdj = exitPrice * (openTrade.isLong() ? -slippagePct : slippagePct);
                    double adjExit = exitPrice + slipAdj;
                    double comm = adjExit * openTrade.getQuantity() * commissionPct;
                    double pnl = openTrade.isLong()
                            ? (adjExit - openTrade.getEntryPrice().doubleValue()) * openTrade.getQuantity()
                            : (openTrade.getEntryPrice().doubleValue() - adjExit) * openTrade.getQuantity();
                    pnl -= comm;
                    totalCommission += comm;

                    openTrade.setExitDate(bar.getTimestamp().toLocalDate());
                    openTrade.setExitPrice(bd(adjExit));
                    openTrade.setExitReason(exitReason);
                    openTrade.setPnl(bd(pnl));
                    openTrade.setPnlPercent(bd(pnl / (openTrade.getEntryPrice().doubleValue() * openTrade.getQuantity()) * 100));
                    openTrade.setHoldingDays((int) openTrade.getEntryDate().until(openTrade.getExitDate(), java.time.temporal.ChronoUnit.DAYS));
                    trades.add(openTrade);

                    capital += pnl;
                    dailyReturns.add(new double[]{pnl / (capital - pnl)});
                    openTrade = null;
                }
            }

            // Get indicators (approximate from data window)
            TechnicalIndicator indicators = approximateIndicators(window, instrument);

            // Generate signals only when no open position
            if (openTrade == null) {
                List<TradeSignal> signals = strategy.generateSignals(window, indicators);
                if (!signals.isEmpty()) {
                    TradeSignal sig = signals.getFirst();
                    double entrySlip = sig.entryPrice() * slippagePct;
                    double adjEntry = sig.isBullish() ? sig.entryPrice() + entrySlip : sig.entryPrice() - entrySlip;
                    double entryComm = adjEntry * commissionPct;
                    int qty = Math.max(1, (int) (capital * 0.1 / adjEntry)); // 10% of capital per trade
                    double totalCost = adjEntry * qty + entryComm * qty;
                    totalSlippage += entrySlip * qty;
                    totalCommission += entryComm * qty;

                    if (totalCost <= capital) {
                        openTrade = BacktestTrade.builder()
                                .backtestResult(result)
                                .instrument(instrument)
                                .entryDate(bar.getTimestamp().toLocalDate())
                                .entryPrice(bd(adjEntry))
                                .exitPrice(bd(sig.targetPrice()))   // temp: store target as exitPrice
                                .quantity(qty)
                                .direction(sig.isBullish() ? TradeDirection.LONG : TradeDirection.SHORT)
                                .stoploss(bd(sig.stopLoss()))
                                .signalType(sig.type())
                                .build();
                    }
                }
            }

            // Drawdown tracking
            peakCapital = Math.max(peakCapital, capital);
            double drawdown = (peakCapital - capital) / peakCapital * 100;
            maxDrawdown = Math.max(maxDrawdown, drawdown);

            daysProcessed++;
            if (daysProcessed % 50 == 0) {
                double returnSoFar = (capital - initialCapital.doubleValue()) / initialCapital.doubleValue() * 100;
                publishProgress(BacktestProgress.progress(
                        result.getId(), result.getName(), strategy.getName(),
                        bar.getTimestamp().toLocalDate(), result.getStartDate(), result.getEndDate(),
                        daysProcessed, totalDays, trades.size(),
                        bd(capital), bd(returnSoFar), bd(maxDrawdown)));
            }
        }

        // Close any still-open trade at last bar close
        if (openTrade != null && !allData.isEmpty()) {
            MarketData lastBar = allData.getLast();
            double closePrice = lastBar.getClose().doubleValue();
            double pnl = openTrade.isLong()
                    ? (closePrice - openTrade.getEntryPrice().doubleValue()) * openTrade.getQuantity()
                    : (openTrade.getEntryPrice().doubleValue() - closePrice) * openTrade.getQuantity();
            openTrade.setExitDate(lastBar.getTimestamp().toLocalDate());
            openTrade.setExitPrice(bd(closePrice));
            openTrade.setExitReason(ExitReason.EXPIRED);
            openTrade.setPnl(bd(pnl));
            openTrade.setPnlPercent(bd(pnl / (openTrade.getEntryPrice().doubleValue() * openTrade.getQuantity()) * 100));
            openTrade.setHoldingDays((int) openTrade.getEntryDate().until(openTrade.getExitDate(), java.time.temporal.ChronoUnit.DAYS));
            trades.add(openTrade);
            capital += pnl;
        }

        // Persist trades
        backtestTradeRepository.saveAll(trades);

        // Compute final metrics
        List<Double> pnls = trades.stream().map(t -> t.getPnl().doubleValue()).toList();
        List<Double> winners = pnls.stream().filter(p -> p > 0).toList();
        List<Double> losers = pnls.stream().filter(p -> p < 0).toList();

        double totalReturn = (capital - initialCapital.doubleValue()) / initialCapital.doubleValue() * 100;
        double years = (double) result.getStartDate().until(result.getEndDate(), java.time.temporal.ChronoUnit.DAYS) / 365.25;
        double annualizedReturn = years > 0
                ? (Math.pow(capital / initialCapital.doubleValue(), 1.0 / years) - 1) * 100 : 0;

        double sharpe = computeSharpe(dailyReturns);
        double sortino = computeSortino(dailyReturns);
        double winRate = trades.isEmpty() ? 0 : (double) winners.size() / trades.size() * 100;
        double avgWin = winners.isEmpty() ? 0 : winners.stream().mapToDouble(d -> d).average().orElse(0);
        double avgLoss = losers.isEmpty() ? 0 : Math.abs(losers.stream().mapToDouble(d -> d).average().orElse(0));
        double grossProfit = winners.stream().mapToDouble(d -> d).sum();
        double grossLoss = Math.abs(losers.stream().mapToDouble(d -> d).sum());
        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : grossProfit > 0 ? 999 : 0;

        result.setFinalCapital(bd(capital));
        result.setTotalReturn(bd(totalReturn));
        result.setAnnualizedReturn(bd(annualizedReturn));
        result.setMaxDrawdown(bd(-maxDrawdown));
        result.setSharpeRatio(bd(sharpe));
        result.setSortinoRatio(bd(sortino));
        result.setWinRate(bd(winRate));
        result.setTotalTrades(trades.size());
        result.setWinningTrades(winners.size());
        result.setLosingTrades(losers.size());
        result.setAvgWin(bd(avgWin));
        result.setAvgLoss(bd(avgLoss));
        result.setProfitFactor(bd(profitFactor));
        result.setCommissionPaid(bd(totalCommission));
        result.setSlippageCost(bd(totalSlippage));
        result.setStatus(BacktestStatus.COMPLETED);
        result.setCompletedAt(OffsetDateTime.now());

        result = backtestResultRepository.save(result);

        publishProgress(BacktestProgress.completed(result.getId(), result.getName(), strategy.getName(),
                result.getStartDate(), result.getEndDate(), totalDays, trades.size(),
                bd(capital), bd(totalReturn), bd(-maxDrawdown)));

        log.info("Backtest {} completed: trades={}, return={:.2f}%, sharpe={:.2f}",
                result.getId(), trades.size(), totalReturn, sharpe);
        return result;
    }

    // ── Indicator approximation ────────────────────────────────────────────────

    private TechnicalIndicator approximateIndicators(List<MarketData> window, Instrument instrument) {
        if (window.size() < 20) return TechnicalIndicator.builder().instrument(instrument).build();

        List<Double> closes = window.stream().map(d -> d.getClose().doubleValue()).toList();

        double ema20 = technicalEngine.calculateEMA(closes, 20);
        double ema50 = closes.size() >= 50 ? technicalEngine.calculateEMA(closes, 50) : ema20;
        double rsi = technicalEngine.calculateRSI(closes, 14);
        TechnicalAnalysisEngine.MACDResult macd = technicalEngine.calculateMACD(closes, 12, 26, 9);
        TechnicalAnalysisEngine.BBResult bb = technicalEngine.calculateBollingerBands(closes, 20, 2.0);
        double vwap = calculateSimpleVWAP(window);

        return TechnicalIndicator.builder()
                .instrument(instrument)
                .timestamp(window.getLast().getTimestamp())
                .ema20(bd(ema20))
                .ema50(bd(ema50))
                .rsi(bd(rsi))
                .macd(bd(macd.macd()))
                .macdSignal(bd(macd.signal()))
                .macdHist(bd(macd.histogram()))
                .bbUpper(bd(bb.upper()))
                .bbMiddle(bd(bb.middle()))
                .bbLower(bd(bb.lower()))
                .vwap(bd(vwap))
                .build();
    }

    private double calculateSimpleVWAP(List<MarketData> data) {
        double numerator = 0;
        double denominator = 0;
        for (MarketData d : data) {
            double tp = (d.getHigh().doubleValue() + d.getLow().doubleValue() + d.getClose().doubleValue()) / 3.0;
            double vol = d.getVolume();
            numerator += tp * vol;
            denominator += vol;
        }
        return denominator > 0 ? numerator / denominator : data.getLast().getClose().doubleValue();
    }

    // ── Statistics helpers ─────────────────────────────────────────────────────

    private double computeSharpe(List<double[]> dailyReturns) {
        if (dailyReturns.size() < 2) return 0;
        double[] rets = dailyReturns.stream().mapToDouble(r -> r[0]).toArray();
        double mean = Arrays.stream(rets).average().orElse(0);
        double variance = Arrays.stream(rets).map(r -> Math.pow(r - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        return stdDev > 0 ? (mean / stdDev) * Math.sqrt(252) : 0;
    }

    private double computeSortino(List<double[]> dailyReturns) {
        if (dailyReturns.size() < 2) return 0;
        double[] rets = dailyReturns.stream().mapToDouble(r -> r[0]).toArray();
        double mean = Arrays.stream(rets).average().orElse(0);
        double[] downside = Arrays.stream(rets).filter(r -> r < 0).map(r -> r * r).toArray();
        if (downside.length == 0) return mean > 0 ? 999 : 0;
        double downsideDev = Math.sqrt(Arrays.stream(downside).average().orElse(0));
        return downsideDev > 0 ? (mean / downsideDev) * Math.sqrt(252) : 0;
    }

    private BigDecimal bd(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private void publishProgress(BacktestProgress progress) {
        try {
            messagingTemplate.convertAndSend("/topic/backtest/" + progress.backtestId(), progress);
        } catch (Exception ex) {
            log.debug("Could not publish backtest progress: {}", ex.getMessage());
        }
    }
}
