package com.nsemcx.trading.service;

import com.nsemcx.trading.model.OptionChain;
import com.nsemcx.trading.repository.OptionChainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OptionChainService {

    private final OptionChainRepository optionChainRepository;

    // Java 21 records for return types
    public record PCRAnalysis(
            double pcr,
            String sentiment,
            String interpretation,
            double ceOI,
            double peOI,
            double changeInPCR
    ) {
        public boolean isBullish() { return pcr > 1.2; }
        public boolean isBearish() { return pcr < 0.8; }
        public boolean isNeutral() { return pcr >= 0.8 && pcr <= 1.2; }
    }

    public record MaxPainAnalysis(
            double maxPainStrike,
            double currentPrice,
            double distanceFromMaxPain,
            double distancePct,
            List<StrikeOI> topStrikesForMaxPain,
            String direction
    ) {}

    public record StrikeOI(double strike, long ceOI, long peOI, long totalOI, double ceIV, double peIV) {}

    public record SupportResistanceFromOI(
            List<Double> supports,
            List<Double> resistances,
            double strongestSupport,
            double strongestResistance,
            String primaryTrend
    ) {}

    public record UnusualOIAlert(
            double strike,
            long ceChangeOI,
            long peChangeOI,
            String alertType,
            String description,
            boolean isBullish
    ) {}

    @Transactional(readOnly = true)
    public PCRAnalysis calculatePCR(Long instrumentId, LocalDate expiry) {
        List<OptionChain> chain = optionChainRepository.findByInstrumentIdAndExpiry(instrumentId, expiry);

        if (chain.isEmpty()) {
            log.warn("No option chain data found for instrumentId: {} expiry: {}", instrumentId, expiry);
            return new PCRAnalysis(1.0, "NEUTRAL", "Insufficient data", 0, 0, 0);
        }

        long totalCeOI = chain.stream().mapToLong(oc -> oc.getCeOi() != null ? oc.getCeOi() : 0).sum();
        long totalPeOI = chain.stream().mapToLong(oc -> oc.getPeOi() != null ? oc.getPeOi() : 0).sum();

        double pcr = totalCeOI > 0 ? (double) totalPeOI / totalCeOI : 1.0;

        // Previous PCR for change calculation (use first entry as baseline)
        double prevPcr = chain.stream()
                .findFirst()
                .map(oc -> oc.getPcr() != null ? oc.getPcr().doubleValue() : pcr)
                .orElse(pcr);
        double pcrChange = pcr - prevPcr;

        String sentiment;
        String interpretation;

        if (pcr >= 1.5) {
            sentiment = "EXTREMELY_BULLISH";
            interpretation = "PCR > 1.5: Extreme put writing indicates strong bullish sentiment. Market makers expect upward movement. Contrarian indicator - could signal overbought conditions.";
        } else if (pcr >= 1.2) {
            sentiment = "BULLISH";
            interpretation = "PCR 1.2-1.5: Strong put-call ratio indicates bullish bias. More puts being written than calls suggests confidence in upside.";
        } else if (pcr >= 0.9) {
            sentiment = "SLIGHTLY_BULLISH";
            interpretation = "PCR 0.9-1.2: Slightly elevated PCR suggests mild bullish sentiment. Market balanced with slight upward bias.";
        } else if (pcr >= 0.7) {
            sentiment = "NEUTRAL_TO_BEARISH";
            interpretation = "PCR 0.7-0.9: Below neutral level, more calls being written. Market participants expect range-bound to slightly bearish movement.";
        } else if (pcr >= 0.5) {
            sentiment = "BEARISH";
            interpretation = "PCR 0.5-0.7: Low PCR indicates bearish sentiment. Heavy call writing suggests resistance at higher levels.";
        } else {
            sentiment = "EXTREMELY_BEARISH";
            interpretation = "PCR < 0.5: Extreme bearish sentiment. Could be contrarian buy signal if PCR is historically low.";
        }

        return new PCRAnalysis(pcr, sentiment, interpretation, totalCeOI, totalPeOI, pcrChange);
    }

    @Transactional(readOnly = true)
    public MaxPainAnalysis calculateMaxPain(Long instrumentId, LocalDate expiry, double currentPrice) {
        List<OptionChain> chain = optionChainRepository.findByInstrumentIdAndExpiry(instrumentId, expiry);

        if (chain.isEmpty()) {
            return new MaxPainAnalysis(currentPrice, currentPrice, 0, 0, List.of(), "NEUTRAL");
        }

        // Max Pain = strike price at which total option writers' losses are minimized
        // = strike at which sum of (CE losses + PE losses) for all other strikes is minimized
        List<Double> strikes = chain.stream()
                .map(oc -> oc.getStrike().doubleValue())
                .distinct()
                .sorted()
                .toList();

        Map<Double, Long> ceOIByStrike = chain.stream()
                .collect(Collectors.toMap(
                        oc -> oc.getStrike().doubleValue(),
                        oc -> oc.getCeOi() != null ? oc.getCeOi() : 0L,
                        Long::sum));

        Map<Double, Long> peOIByStrike = chain.stream()
                .collect(Collectors.toMap(
                        oc -> oc.getStrike().doubleValue(),
                        oc -> oc.getPeOi() != null ? oc.getPeOi() : 0L,
                        Long::sum));

        double maxPainStrike = currentPrice;
        double minTotalPain = Double.MAX_VALUE;

        for (double testStrike : strikes) {
            // CE pain: sum of (strike - testStrike) for all strikes < testStrike (ITM calls)
            double cePain = 0;
            for (Map.Entry<Double, Long> entry : ceOIByStrike.entrySet()) {
                double s = entry.getKey();
                if (s < testStrike) {
                    cePain += (testStrike - s) * entry.getValue();
                }
            }

            // PE pain: sum of (testStrike - strike) for all strikes > testStrike (ITM puts)
            double pePain = 0;
            for (Map.Entry<Double, Long> entry : peOIByStrike.entrySet()) {
                double s = entry.getKey();
                if (s > testStrike) {
                    pePain += (s - testStrike) * entry.getValue();
                }
            }

            double totalPain = cePain + pePain;
            if (totalPain < minTotalPain) {
                minTotalPain = totalPain;
                maxPainStrike = testStrike;
            }
        }

        double distance = currentPrice - maxPainStrike;
        double distancePct = maxPainStrike != 0 ? (distance / maxPainStrike) * 100 : 0;
        String direction = distance > 0 ? "ABOVE_MAX_PAIN" : "BELOW_MAX_PAIN";

        // Top strikes by total OI
        List<StrikeOI> topStrikes = strikes.stream()
                .map(strike -> {
                    long ceOI = ceOIByStrike.getOrDefault(strike, 0L);
                    long peOI = peOIByStrike.getOrDefault(strike, 0L);
                    OptionalDouble ceIV = chain.stream()
                            .filter(oc -> oc.getStrike().doubleValue() == strike && oc.getCeIv() != null)
                            .mapToDouble(oc -> oc.getCeIv().doubleValue()).average();
                    OptionalDouble peIV = chain.stream()
                            .filter(oc -> oc.getStrike().doubleValue() == strike && oc.getPeIv() != null)
                            .mapToDouble(oc -> oc.getPeIv().doubleValue()).average();
                    return new StrikeOI(strike, ceOI, peOI, ceOI + peOI,
                            ceIV.orElse(0), peIV.orElse(0));
                })
                .sorted(Comparator.comparingLong(StrikeOI::totalOI).reversed())
                .limit(10)
                .toList();

        return new MaxPainAnalysis(maxPainStrike, currentPrice, distance, distancePct, topStrikes, direction);
    }

    @Transactional(readOnly = true)
    public SupportResistanceFromOI identifySupportResistance(Long instrumentId, LocalDate expiry, double currentPrice) {
        List<OptionChain> chain = optionChainRepository.findByInstrumentIdAndExpiry(instrumentId, expiry);

        if (chain.isEmpty()) {
            return new SupportResistanceFromOI(List.of(), List.of(), currentPrice, currentPrice, "NEUTRAL");
        }

        // High PE OI strikes below current price = support
        // High CE OI strikes above current price = resistance
        List<Double> supports = new ArrayList<>();
        List<Double> resistances = new ArrayList<>();

        long avgOI = chain.stream()
                .mapToLong(oc -> (oc.getCeOi() != null ? oc.getCeOi() : 0) + (oc.getPeOi() != null ? oc.getPeOi() : 0))
                .sum() / Math.max(chain.size(), 1);

        for (OptionChain oc : chain) {
            double strike = oc.getStrike().doubleValue();
            long peOI = oc.getPeOi() != null ? oc.getPeOi() : 0;
            long ceOI = oc.getCeOi() != null ? oc.getCeOi() : 0;

            if (strike < currentPrice && peOI > avgOI * 1.5) {
                supports.add(strike);
            }
            if (strike > currentPrice && ceOI > avgOI * 1.5) {
                resistances.add(strike);
            }
        }

        // Sort supports descending (closest below current price first)
        supports.sort(Comparator.reverseOrder());
        // Sort resistances ascending (closest above current price first)
        resistances.sort(Comparator.naturalOrder());

        double strongestSupport = supports.isEmpty() ? currentPrice * 0.95 : supports.get(0);
        double strongestResistance = resistances.isEmpty() ? currentPrice * 1.05 : resistances.get(0);

        // Determine trend based on PCR
        long totalCE = chain.stream().mapToLong(oc -> oc.getCeOi() != null ? oc.getCeOi() : 0).sum();
        long totalPE = chain.stream().mapToLong(oc -> oc.getPeOi() != null ? oc.getPeOi() : 0).sum();
        double pcr = totalCE > 0 ? (double) totalPE / totalCE : 1.0;

        String trend = pcr > 1.2 ? "BULLISH" : pcr < 0.8 ? "BEARISH" : "NEUTRAL";

        return new SupportResistanceFromOI(supports, resistances, strongestSupport, strongestResistance, trend);
    }

    @Transactional(readOnly = true)
    public List<UnusualOIAlert> detectUnusualOIBuildup(Long instrumentId, LocalDate expiry) {
        long avgOI = 100_000L; // Conservative threshold
        List<OptionChain> unusualEntries = optionChainRepository.findUnusualOIBuildup(instrumentId, expiry, avgOI);

        List<UnusualOIAlert> alerts = new ArrayList<>();

        for (OptionChain oc : unusualEntries) {
            double strike = oc.getStrike().doubleValue();
            long ceChange = oc.getCeChangeOi() != null ? oc.getCeChangeOi() : 0;
            long peChange = oc.getPeChangeOi() != null ? oc.getPeChangeOi() : 0;

            if (ceChange > avgOI) {
                // Unusual call writing - bearish signal (resistance being built)
                alerts.add(new UnusualOIAlert(
                        strike, ceChange, peChange,
                        "UNUSUAL_CALL_BUILDUP",
                        String.format("Unusual call OI buildup at %.0f: %,d new contracts (likely resistance)", strike, ceChange),
                        false
                ));
            }

            if (peChange > avgOI) {
                // Unusual put writing - bullish signal (support being built)
                alerts.add(new UnusualOIAlert(
                        strike, ceChange, peChange,
                        "UNUSUAL_PUT_BUILDUP",
                        String.format("Unusual put OI buildup at %.0f: %,d new contracts (likely support)", strike, peChange),
                        true
                ));
            }
        }

        alerts.sort(Comparator.comparingLong((UnusualOIAlert a) -> a.ceChangeOI() + a.peChangeOI()).reversed());
        return alerts;
    }

    @Transactional
    public OptionChain saveOptionChainEntry(OptionChain entry) {
        // Recalculate PCR before saving
        if (entry.getCeOi() != null && entry.getCeOi() > 0) {
            double pcr = (double) (entry.getPeOi() != null ? entry.getPeOi() : 0) / entry.getCeOi();
            entry.setPcr(BigDecimal.valueOf(pcr));
        }
        return optionChainRepository.save(entry);
    }
}
