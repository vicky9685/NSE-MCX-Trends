package com.nsemcx.trading.indicators;

import com.nsemcx.trading.model.MarketData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TechnicalAnalysisEngine {

    // ========== Java 21 Records for return types ==========

    public record MACDResult(double macd, double signal, double histogram) {
        public boolean isBullish() { return histogram > 0; }
        public boolean isBearishCrossover() { return macd < signal && histogram < 0; }
        public boolean isBullishCrossover() { return macd > signal && histogram > 0; }
    }

    public record BBResult(double upper, double middle, double lower, double bandwidth, double percentB) {
        public boolean isAboveUpper(double price) { return price > upper; }
        public boolean isBelowLower(double price) { return price < lower; }
        public boolean isSqueezing() { return bandwidth < 0.05; }
    }

    public record ADXResult(double adx, double diPlus, double diMinus) {
        public boolean isTrending() { return adx > 25; }
        public boolean isStrongTrend() { return adx > 40; }
        public boolean isBullishTrend() { return diPlus > diMinus && adx > 25; }
        public boolean isBearishTrend() { return diMinus > diPlus && adx > 25; }
    }

    public record VWAPResult(double vwap, double upperBand, double lowerBand) {
        public boolean isPriceAboveVWAP(double price) { return price > vwap; }
        public boolean isPriceBelowVWAP(double price) { return price < vwap; }
    }

    // ========== Sealed interface for Candle Patterns ==========

    public sealed interface CandlePattern permits
            CandlePattern.Doji,
            CandlePattern.Hammer,
            CandlePattern.InvertedHammer,
            CandlePattern.ShootingStar,
            CandlePattern.BullishEngulfing,
            CandlePattern.BearishEngulfing,
            CandlePattern.MorningStar,
            CandlePattern.EveningStar,
            CandlePattern.ThreeWhiteSoldiers,
            CandlePattern.ThreeBlackCrows,
            CandlePattern.PiercingLine,
            CandlePattern.DarkCloudCover,
            CandlePattern.NoClearPattern {

        String name();
        boolean isBullish();

        record Doji(double bodyToRangeRatio) implements CandlePattern {
            public String name() { return "Doji"; }
            public boolean isBullish() { return false; } // Doji is neutral/indecision
        }

        record Hammer(double shadowToBodyRatio) implements CandlePattern {
            public String name() { return "Hammer"; }
            public boolean isBullish() { return true; }
        }

        record InvertedHammer(double shadowToBodyRatio) implements CandlePattern {
            public String name() { return "Inverted Hammer"; }
            public boolean isBullish() { return true; }
        }

        record ShootingStar(double shadowToBodyRatio) implements CandlePattern {
            public String name() { return "Shooting Star"; }
            public boolean isBullish() { return false; }
        }

        record BullishEngulfing(double engulfingRatio) implements CandlePattern {
            public String name() { return "Bullish Engulfing"; }
            public boolean isBullish() { return true; }
        }

        record BearishEngulfing(double engulfingRatio) implements CandlePattern {
            public String name() { return "Bearish Engulfing"; }
            public boolean isBullish() { return false; }
        }

        record MorningStar(double gapDown, double gapUp) implements CandlePattern {
            public String name() { return "Morning Star"; }
            public boolean isBullish() { return true; }
        }

        record EveningStar(double gapUp, double gapDown) implements CandlePattern {
            public String name() { return "Evening Star"; }
            public boolean isBullish() { return false; }
        }

        record ThreeWhiteSoldiers(double avgBodySize) implements CandlePattern {
            public String name() { return "Three White Soldiers"; }
            public boolean isBullish() { return true; }
        }

        record ThreeBlackCrows(double avgBodySize) implements CandlePattern {
            public String name() { return "Three Black Crows"; }
            public boolean isBullish() { return false; }
        }

        record PiercingLine(double penetrationPercent) implements CandlePattern {
            public String name() { return "Piercing Line"; }
            public boolean isBullish() { return true; }
        }

        record DarkCloudCover(double penetrationPercent) implements CandlePattern {
            public String name() { return "Dark Cloud Cover"; }
            public boolean isBullish() { return false; }
        }

        record NoClearPattern() implements CandlePattern {
            public String name() { return "No Clear Pattern"; }
            public boolean isBullish() { return false; }
        }
    }

    // ========== RSI using Wilder's Smoothing ==========

    public double calculateRSI(List<Double> closes, int period) {
        if (closes == null || closes.size() < period + 1) {
            log.warn("Insufficient data for RSI calculation. Need {}, got {}", period + 1, closes == null ? 0 : closes.size());
            return 50.0; // neutral default
        }

        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();

        for (int i = 1; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            gains.add(Math.max(change, 0));
            losses.add(Math.max(-change, 0));
        }

        // First average gain/loss (simple average of first `period` values)
        double avgGain = 0;
        double avgLoss = 0;
        for (int i = 0; i < period; i++) {
            avgGain += gains.get(i);
            avgLoss += losses.get(i);
        }
        avgGain /= period;
        avgLoss /= period;

        // Wilder's smoothing for remaining periods
        for (int i = period; i < gains.size(); i++) {
            avgGain = (avgGain * (period - 1) + gains.get(i)) / period;
            avgLoss = (avgLoss * (period - 1) + losses.get(i)) / period;
        }

        if (avgLoss == 0) return 100.0;

        double rs = avgGain / avgLoss;
        return BigDecimal.valueOf(100 - (100 / (1 + rs)))
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // ========== MACD ==========

    public MACDResult calculateMACD(List<Double> closes) {
        return calculateMACD(closes, 12, 26, 9);
    }

    public MACDResult calculateMACD(List<Double> closes, int fastPeriod, int slowPeriod, int signalPeriod) {
        if (closes == null || closes.size() < slowPeriod + signalPeriod) {
            log.warn("Insufficient data for MACD. Need {}, got {}", slowPeriod + signalPeriod, closes == null ? 0 : closes.size());
            return new MACDResult(0, 0, 0);
        }

        List<Double> fastEMAs = calculateEMAList(closes, fastPeriod);
        List<Double> slowEMAs = calculateEMAList(closes, slowPeriod);

        // MACD line = fastEMA - slowEMA (aligned to end of list)
        int offset = slowPeriod - fastPeriod;
        List<Double> macdLine = new ArrayList<>();
        for (int i = 0; i < slowEMAs.size(); i++) {
            macdLine.add(fastEMAs.get(i + offset) - slowEMAs.get(i));
        }

        // Signal line = EMA of MACD line
        List<Double> signalLine = calculateEMAList(macdLine, signalPeriod);

        double lastMACD = macdLine.get(macdLine.size() - 1);
        double lastSignal = signalLine.get(signalLine.size() - 1);
        double histogram = lastMACD - lastSignal;

        return new MACDResult(
                round4(lastMACD),
                round4(lastSignal),
                round4(histogram)
        );
    }

    // ========== EMA ==========

    public double calculateEMA(List<Double> closes, int period) {
        List<Double> emaList = calculateEMAList(closes, period);
        return emaList.isEmpty() ? 0 : emaList.get(emaList.size() - 1);
    }

    private List<Double> calculateEMAList(List<Double> closes, int period) {
        if (closes == null || closes.size() < period) {
            return new ArrayList<>();
        }

        List<Double> emas = new ArrayList<>();
        double multiplier = 2.0 / (period + 1);

        // Start with SMA for first EMA value
        double sma = 0;
        for (int i = 0; i < period; i++) {
            sma += closes.get(i);
        }
        sma /= period;
        emas.add(sma);

        // Subsequent EMAs
        for (int i = period; i < closes.size(); i++) {
            double ema = (closes.get(i) - emas.get(emas.size() - 1)) * multiplier + emas.get(emas.size() - 1);
            emas.add(ema);
        }

        return emas;
    }

    // ========== Bollinger Bands ==========

    public BBResult calculateBollingerBands(List<Double> closes, int period, double stdDevMultiplier) {
        if (closes == null || closes.size() < period) {
            log.warn("Insufficient data for Bollinger Bands. Need {}, got {}", period, closes == null ? 0 : closes.size());
            return new BBResult(0, 0, 0, 0, 0);
        }

        List<Double> recent = closes.subList(closes.size() - period, closes.size());

        // Calculate SMA (middle band)
        double sma = recent.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // Calculate standard deviation
        double variance = recent.stream()
                .mapToDouble(price -> Math.pow(price - sma, 2))
                .average()
                .orElse(0);
        double stdDev = Math.sqrt(variance);

        double upper = sma + stdDevMultiplier * stdDev;
        double lower = sma - stdDevMultiplier * stdDev;

        // Bandwidth = (upper - lower) / middle
        double bandwidth = sma != 0 ? (upper - lower) / sma : 0;

        // %B = (price - lower) / (upper - lower)
        double lastPrice = closes.get(closes.size() - 1);
        double percentB = (upper - lower) != 0 ? (lastPrice - lower) / (upper - lower) : 0.5;

        return new BBResult(round4(upper), round4(sma), round4(lower), round4(bandwidth), round4(percentB));
    }

    // ========== VWAP ==========

    public VWAPResult calculateVWAP(List<MarketData> data) {
        if (data == null || data.isEmpty()) {
            return new VWAPResult(0, 0, 0);
        }

        double cumulativeTPV = 0; // Typical Price * Volume
        double cumulativeVolume = 0;
        List<Double> typicalPrices = new ArrayList<>();

        for (MarketData md : data) {
            double typicalPrice = (md.getHigh().doubleValue() + md.getLow().doubleValue() + md.getClose().doubleValue()) / 3.0;
            double volume = md.getVolume() != null ? md.getVolume() : 0;
            cumulativeTPV += typicalPrice * volume;
            cumulativeVolume += volume;
            typicalPrices.add(typicalPrice);
        }

        double vwap = cumulativeVolume > 0 ? cumulativeTPV / cumulativeVolume : 0;

        // Calculate standard deviation of typical prices for bands
        double meanTP = typicalPrices.stream().mapToDouble(Double::doubleValue).average().orElse(vwap);
        double stdDev = Math.sqrt(typicalPrices.stream()
                .mapToDouble(tp -> Math.pow(tp - meanTP, 2))
                .average()
                .orElse(0));

        return new VWAPResult(round4(vwap), round4(vwap + stdDev), round4(vwap - stdDev));
    }

    // ========== ADX ==========

    public ADXResult calculateADX(List<MarketData> data, int period) {
        if (data == null || data.size() < period * 2) {
            log.warn("Insufficient data for ADX. Need {}, got {}", period * 2, data == null ? 0 : data.size());
            return new ADXResult(0, 0, 0);
        }

        List<Double> trueRanges = new ArrayList<>();
        List<Double> dmPlus = new ArrayList<>();
        List<Double> dmMinus = new ArrayList<>();

        for (int i = 1; i < data.size(); i++) {
            MarketData curr = data.get(i);
            MarketData prev = data.get(i - 1);

            double high = curr.getHigh().doubleValue();
            double low = curr.getLow().doubleValue();
            double prevClose = prev.getClose().doubleValue();
            double prevHigh = prev.getHigh().doubleValue();
            double prevLow = prev.getLow().doubleValue();

            // True Range
            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            trueRanges.add(tr);

            // Directional Movement
            double upMove = high - prevHigh;
            double downMove = prevLow - low;

            if (upMove > downMove && upMove > 0) {
                dmPlus.add(upMove);
                dmMinus.add(0.0);
            } else if (downMove > upMove && downMove > 0) {
                dmPlus.add(0.0);
                dmMinus.add(downMove);
            } else {
                dmPlus.add(0.0);
                dmMinus.add(0.0);
            }
        }

        // Wilder's smoothed averages
        double smoothedTR = wilderSmooth(trueRanges, period);
        double smoothedDMPlus = wilderSmooth(dmPlus, period);
        double smoothedDMMinus = wilderSmooth(dmMinus, period);

        double diPlus = smoothedTR != 0 ? (smoothedDMPlus / smoothedTR) * 100 : 0;
        double diMinus = smoothedTR != 0 ? (smoothedDMMinus / smoothedTR) * 100 : 0;

        double diSum = diPlus + diMinus;
        double dx = diSum != 0 ? Math.abs(diPlus - diMinus) / diSum * 100 : 0;

        // ADX = smoothed DX
        List<Double> dxList = new ArrayList<>();
        for (int i = 0; i < trueRanges.size() - period + 1; i++) {
            double tr = 0, dmp = 0, dmm = 0;
            for (int j = i; j < i + period; j++) {
                tr += trueRanges.get(j);
                dmp += dmPlus.get(j);
                dmm += dmMinus.get(j);
            }
            double dip = tr != 0 ? (dmp / tr) * 100 : 0;
            double dim = tr != 0 ? (dmm / tr) * 100 : 0;
            double s = dip + dim;
            dxList.add(s != 0 ? Math.abs(dip - dim) / s * 100 : 0);
        }

        double adx = dxList.stream().mapToDouble(Double::doubleValue).average().orElse(dx);

        return new ADXResult(round4(adx), round4(diPlus), round4(diMinus));
    }

    private double wilderSmooth(List<Double> values, int period) {
        if (values.size() < period) return 0;

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += values.get(i);
        }
        double smoothed = sum;

        for (int i = period; i < values.size(); i++) {
            smoothed = smoothed - (smoothed / period) + values.get(i);
        }

        return smoothed / period;
    }

    // ========== Candle Pattern Identification ==========

    public CandlePattern identifyCandlePattern(List<MarketData> recent) {
        if (recent == null || recent.isEmpty()) {
            return new CandlePattern.NoClearPattern();
        }

        // Need at least 1 candle; use 3 for multi-candle patterns
        MarketData last = recent.get(recent.size() - 1);
        double open = last.getOpen().doubleValue();
        double high = last.getHigh().doubleValue();
        double low = last.getLow().doubleValue();
        double close = last.getClose().doubleValue();
        double range = high - low;
        double body = Math.abs(close - open);

        if (range == 0) return new CandlePattern.NoClearPattern();

        double bodyRatio = body / range;
        double upperShadow = high - Math.max(open, close);
        double lowerShadow = Math.min(open, close) - low;

        // Doji: body is very small relative to range
        if (bodyRatio < 0.1) {
            return new CandlePattern.Doji(bodyRatio);
        }

        // Hammer: small body at top, long lower shadow (2x body), small upper shadow, bullish context
        if (body > 0 && lowerShadow >= 2 * body && upperShadow <= 0.3 * body) {
            return new CandlePattern.Hammer(lowerShadow / body);
        }

        // Shooting Star: small body at bottom, long upper shadow, small lower shadow, bearish context
        if (body > 0 && upperShadow >= 2 * body && lowerShadow <= 0.3 * body) {
            return new CandlePattern.ShootingStar(upperShadow / body);
        }

        // Inverted Hammer: small body at bottom, long upper shadow (like shooting star but in downtrend)
        if (body > 0 && upperShadow >= 2 * body && lowerShadow <= 0.5 * body && close > open) {
            return new CandlePattern.InvertedHammer(upperShadow / body);
        }

        // Two-candle patterns require at least 2 candles
        if (recent.size() >= 2) {
            MarketData prev = recent.get(recent.size() - 2);
            double prevOpen = prev.getOpen().doubleValue();
            double prevClose = prev.getClose().doubleValue();
            double prevBody = Math.abs(prevClose - prevOpen);

            // Bullish Engulfing: prev is bearish, current is bullish and engulfs prev body
            if (prevClose < prevOpen && close > open
                    && open <= prevClose && close >= prevOpen
                    && body > prevBody) {
                return new CandlePattern.BullishEngulfing(body / prevBody);
            }

            // Bearish Engulfing: prev is bullish, current is bearish and engulfs prev body
            if (prevClose > prevOpen && close < open
                    && open >= prevClose && close <= prevOpen
                    && body > prevBody) {
                return new CandlePattern.BearishEngulfing(body / prevBody);
            }

            // Piercing Line: prev is bearish, current opens below prev low, closes above midpoint of prev body
            if (prevClose < prevOpen && close > open
                    && open < prev.getLow().doubleValue()
                    && close > (prevOpen + prevClose) / 2 && close < prevOpen) {
                double penetration = (close - prevClose) / prevBody;
                return new CandlePattern.PiercingLine(penetration);
            }

            // Dark Cloud Cover: prev is bullish, current opens above prev high, closes below midpoint
            if (prevClose > prevOpen && close < open
                    && open > prev.getHigh().doubleValue()
                    && close < (prevOpen + prevClose) / 2 && close > prevOpen) {
                double penetration = (prevClose - close) / prevBody;
                return new CandlePattern.DarkCloudCover(penetration);
            }
        }

        // Three-candle patterns
        if (recent.size() >= 3) {
            MarketData prev1 = recent.get(recent.size() - 2);
            MarketData prev2 = recent.get(recent.size() - 3);

            double p1Open = prev1.getOpen().doubleValue();
            double p1Close = prev1.getClose().doubleValue();
            double p2Open = prev2.getOpen().doubleValue();
            double p2Close = prev2.getClose().doubleValue();

            // Three White Soldiers: three consecutive bullish candles with higher closes
            if (close > open && p1Close > p1Open && p2Close > p2Open
                    && close > p1Close && p1Close > p2Close
                    && open > p1Open && p1Open > p2Open) {
                double avgBody = (body + Math.abs(p1Close - p1Open) + Math.abs(p2Close - p2Open)) / 3;
                return new CandlePattern.ThreeWhiteSoldiers(avgBody);
            }

            // Three Black Crows: three consecutive bearish candles with lower closes
            if (close < open && p1Close < p1Open && p2Close < p2Open
                    && close < p1Close && p1Close < p2Close
                    && open < p1Open && p1Open < p2Open) {
                double avgBody = (body + Math.abs(p1Close - p1Open) + Math.abs(p2Close - p2Open)) / 3;
                return new CandlePattern.ThreeBlackCrows(avgBody);
            }

            // Morning Star: bearish, small body (star), bullish
            if (p2Close < p2Open && Math.abs(p1Close - p1Open) / (p1.getHigh().doubleValue() - p1.getLow().doubleValue()) < 0.2
                    && close > open && close > (p2Open + p2Close) / 2) {
                return new CandlePattern.MorningStar(p2Close - p1.getHigh().doubleValue(), p1.getLow().doubleValue() - open);
            }

            // Evening Star: bullish, small body (star), bearish
            if (p2Close > p2Open && Math.abs(p1Close - p1Open) / (p1.getHigh().doubleValue() - p1.getLow().doubleValue()) < 0.2
                    && close < open && close < (p2Open + p2Close) / 2) {
                return new CandlePattern.EveningStar(p1.getLow().doubleValue() - p2Close, open - p1.getHigh().doubleValue());
            }
        }

        return new CandlePattern.NoClearPattern();
    }

    // ========== ATR (Average True Range) ==========

    public double calculateATR(List<MarketData> data, int period) {
        if (data == null || data.size() < period + 1) {
            return 0;
        }

        List<Double> trueRanges = new ArrayList<>();
        for (int i = 1; i < data.size(); i++) {
            MarketData curr = data.get(i);
            MarketData prev = data.get(i - 1);
            double high = curr.getHigh().doubleValue();
            double low = curr.getLow().doubleValue();
            double prevClose = prev.getClose().doubleValue();
            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            trueRanges.add(tr);
        }

        // Simple average of first `period` TRs for initial ATR
        double atr = 0;
        for (int i = 0; i < Math.min(period, trueRanges.size()); i++) {
            atr += trueRanges.get(i);
        }
        atr /= period;

        // Wilder's smoothing
        for (int i = period; i < trueRanges.size(); i++) {
            atr = (atr * (period - 1) + trueRanges.get(i)) / period;
        }

        return round4(atr);
    }

    // ========== Stochastic Oscillator ==========

    public record StochasticResult(double k, double d) {
        public boolean isOversold() { return k < 20; }
        public boolean isOverbought() { return k > 80; }
    }

    public StochasticResult calculateStochastic(List<MarketData> data, int kPeriod, int dPeriod) {
        if (data == null || data.size() < kPeriod + dPeriod) {
            return new StochasticResult(50, 50);
        }

        List<Double> kValues = new ArrayList<>();
        for (int i = kPeriod - 1; i < data.size(); i++) {
            List<MarketData> window = data.subList(i - kPeriod + 1, i + 1);
            double highestHigh = window.stream().mapToDouble(md -> md.getHigh().doubleValue()).max().orElse(0);
            double lowestLow = window.stream().mapToDouble(md -> md.getLow().doubleValue()).min().orElse(0);
            double close = data.get(i).getClose().doubleValue();
            double k = highestHigh != lowestLow ? ((close - lowestLow) / (highestHigh - lowestLow)) * 100 : 50;
            kValues.add(k);
        }

        double lastK = kValues.get(kValues.size() - 1);

        // %D = SMA of %K
        double lastD = kValues.subList(Math.max(0, kValues.size() - dPeriod), kValues.size())
                .stream().mapToDouble(Double::doubleValue).average().orElse(50);

        return new StochasticResult(round4(lastK), round4(lastD));
    }

    // ========== Support and Resistance Levels ==========

    public record SupportResistanceResult(List<Double> supports, List<Double> resistances) {}

    public SupportResistanceResult identifySupportResistance(List<MarketData> data, int lookback) {
        if (data == null || data.size() < lookback * 2 + 1) {
            return new SupportResistanceResult(List.of(), List.of());
        }

        List<Double> supports = new ArrayList<>();
        List<Double> resistances = new ArrayList<>();

        for (int i = lookback; i < data.size() - lookback; i++) {
            double currentLow = data.get(i).getLow().doubleValue();
            double currentHigh = data.get(i).getHigh().doubleValue();
            boolean isSwingLow = true;
            boolean isSwingHigh = true;

            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue;
                if (data.get(j).getLow().doubleValue() < currentLow) isSwingLow = false;
                if (data.get(j).getHigh().doubleValue() > currentHigh) isSwingHigh = false;
            }

            if (isSwingLow) supports.add(round4(currentLow));
            if (isSwingHigh) resistances.add(round4(currentHigh));
        }

        return new SupportResistanceResult(supports, resistances);
    }

    // ========== Volume Analysis ==========

    public double calculateVolumeRatio(List<MarketData> data, int period) {
        if (data == null || data.size() < period + 1) return 1.0;

        double currentVolume = data.get(data.size() - 1).getVolume();
        List<MarketData> historicalWindow = data.subList(data.size() - period - 1, data.size() - 1);
        double avgVolume = historicalWindow.stream()
                .mapToDouble(md -> md.getVolume() != null ? md.getVolume() : 0)
                .average()
                .orElse(1);

        return avgVolume > 0 ? round4(currentVolume / avgVolume) : 1.0;
    }

    // ========== Utility ==========

    private double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    public List<Double> extractClosePrices(List<MarketData> data) {
        return data.stream()
                .map(md -> md.getClose().doubleValue())
                .toList();
    }

    public String describeCandlePattern(CandlePattern pattern) {
        return switch (pattern) {
            case CandlePattern.Doji d -> "Doji pattern detected (body ratio: %.2f) - market indecision".formatted(d.bodyToRangeRatio());
            case CandlePattern.Hammer h -> "Hammer pattern - potential bullish reversal (shadow ratio: %.2f)".formatted(h.shadowToBodyRatio());
            case CandlePattern.InvertedHammer ih -> "Inverted Hammer - potential bullish reversal";
            case CandlePattern.ShootingStar ss -> "Shooting Star - potential bearish reversal (shadow ratio: %.2f)".formatted(ss.shadowToBodyRatio());
            case CandlePattern.BullishEngulfing be -> "Bullish Engulfing - strong bullish reversal signal (ratio: %.2f)".formatted(be.engulfingRatio());
            case CandlePattern.BearishEngulfing be -> "Bearish Engulfing - strong bearish reversal signal (ratio: %.2f)".formatted(be.engulfingRatio());
            case CandlePattern.MorningStar ms -> "Morning Star - three-candle bullish reversal pattern";
            case CandlePattern.EveningStar es -> "Evening Star - three-candle bearish reversal pattern";
            case CandlePattern.ThreeWhiteSoldiers tws -> "Three White Soldiers - strong bullish continuation";
            case CandlePattern.ThreeBlackCrows tbc -> "Three Black Crows - strong bearish continuation";
            case CandlePattern.PiercingLine pl -> "Piercing Line - bullish reversal (penetration: %.0f%%)".formatted(pl.penetrationPercent() * 100);
            case CandlePattern.DarkCloudCover dcc -> "Dark Cloud Cover - bearish reversal (penetration: %.0f%%)".formatted(dcc.penetrationPercent() * 100);
            case CandlePattern.NoClearPattern ncp -> "No clear candlestick pattern identified";
        };
    }
}
