package com.nsemcx.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nsemcx.trading.model.Instrument;
import com.nsemcx.trading.model.MarketData;
import com.nsemcx.trading.repository.InstrumentRepository;
import com.nsemcx.trading.repository.MarketDataRepository;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class YahooFinanceService {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataRepository marketDataRepository;

    @Value("${yahoo.finance.v8-url:https://query1.finance.yahoo.com/v8/finance/chart}")
    private String yahooV8Url;

    @Value("${yahoo.finance.rate-limit-delay-ms:1000}")
    private long rateLimitDelayMs;

    @Value("${yahoo.finance.max-retries:3}")
    private int maxRetries;

    @Value("${yahoo.finance.timeout-seconds:30}")
    private int timeoutSeconds;

    // Default instruments to track
    private static final List<String> DEFAULT_NSE_SYMBOLS = List.of(
            "RELIANCE.NS", "TCS.NS", "HDFCBANK.NS", "INFY.NS",
            "ICICIBANK.NS", "SBIN.NS", "^NSEI", "^NSEBANK"
    );

    private static final List<String> DEFAULT_MCX_SYMBOLS = List.of(
            "GC=F", "SI=F", "CL=F", "NG=F", "HG=F"
    );

    @Transactional
    public List<MarketData> fetchHistoricalData(String yahooSymbol, String interval, String period) {
        log.info("Fetching historical data for symbol: {} interval: {} period: {}", yahooSymbol, interval, period);

        String url = String.format("%s/%s?interval=%s&range=%s&includePrePost=false",
                yahooV8Url, yahooSymbol, interval, period);

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    long backoffMs = rateLimitDelayMs * (long) Math.pow(2, attempt - 1);
                    log.debug("Retry attempt {} for {} after {}ms", attempt, yahooSymbol, backoffMs);
                    Thread.sleep(backoffMs);
                }

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (compatible; NSE-MCX-Trading/1.0)")
                        .header("Accept", "application/json")
                        .build();

                try (Response response = okHttpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        log.warn("Yahoo Finance returned status {} for {}", response.code(), yahooSymbol);
                        if (response.code() == 429) {
                            // Rate limited, wait longer
                            Thread.sleep(rateLimitDelayMs * 5);
                            continue;
                        }
                        continue;
                    }

                    String responseBody = response.body() != null ? response.body().string() : null;
                    if (responseBody == null || responseBody.isEmpty()) {
                        log.warn("Empty response from Yahoo Finance for {}", yahooSymbol);
                        continue;
                    }

                    return parseAndStoreOHLCV(yahooSymbol, responseBody);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while fetching data for {}", yahooSymbol);
                break;
            } catch (IOException e) {
                log.error("IO error fetching data for {}: {}", yahooSymbol, e.getMessage());
                if (attempt == maxRetries - 1) {
                    log.error("All retries exhausted for {}", yahooSymbol);
                }
            } catch (Exception e) {
                log.error("Unexpected error fetching data for {}: {}", yahooSymbol, e.getMessage(), e);
                break;
            }
        }

        return new ArrayList<>();
    }

    @Transactional
    public List<MarketData> fetchOneYearHistory(String yahooSymbol) {
        return fetchHistoricalData(yahooSymbol, "1d", "1y");
    }

    @Transactional
    public List<MarketData> refreshAllInstruments() {
        List<MarketData> allData = new ArrayList<>();
        List<Instrument> instruments = instrumentRepository.findByIsActiveTrue();

        for (Instrument instrument : instruments) {
            try {
                String yahooSymbol = instrument.getYahooSymbol();
                log.info("Refreshing data for instrument: {} ({})", instrument.getSymbol(), yahooSymbol);

                List<MarketData> data = fetchOneYearHistory(yahooSymbol);
                allData.addAll(data);

                // Rate limiting between requests
                Thread.sleep(rateLimitDelayMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted during refresh for {}", instrument.getSymbol());
                break;
            } catch (Exception e) {
                log.error("Error refreshing data for {}: {}", instrument.getSymbol(), e.getMessage());
            }
        }

        log.info("Refreshed {} market data records for {} instruments", allData.size(), instruments.size());
        return allData;
    }

    private List<MarketData> parseAndStoreOHLCV(String yahooSymbol, String jsonResponse) throws IOException {
        List<MarketData> result = new ArrayList<>();

        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode chart = root.path("chart");

        if (chart.isMissingNode()) {
            log.warn("No 'chart' node in Yahoo Finance response for {}", yahooSymbol);
            return result;
        }

        JsonNode errorNode = chart.path("error");
        if (!errorNode.isNull() && !errorNode.isMissingNode()) {
            log.warn("Yahoo Finance returned error for {}: {}", yahooSymbol, errorNode);
            return result;
        }

        JsonNode resultArray = chart.path("result");
        if (!resultArray.isArray() || resultArray.isEmpty()) {
            log.warn("Empty result array from Yahoo Finance for {}", yahooSymbol);
            return result;
        }

        JsonNode data = resultArray.get(0);
        JsonNode timestamps = data.path("timestamp");
        JsonNode indicators = data.path("indicators");
        JsonNode quote = indicators.path("quote");

        if (!quote.isArray() || quote.isEmpty()) {
            log.warn("No quote data in Yahoo Finance response for {}", yahooSymbol);
            return result;
        }

        JsonNode quoteData = quote.get(0);
        JsonNode opens = quoteData.path("open");
        JsonNode highs = quoteData.path("high");
        JsonNode lows = quoteData.path("low");
        JsonNode closes = quoteData.path("close");
        JsonNode volumes = quoteData.path("volume");

        // Check for adjusted close
        JsonNode adjclose = indicators.path("adjclose");
        JsonNode adjCloses = adjclose.isArray() && !adjclose.isEmpty()
                ? adjclose.get(0).path("adjclose")
                : null;

        // Find the instrument in DB using the cleaned symbol
        String cleanSymbol = cleanSymbolForDB(yahooSymbol);
        Optional<Instrument> instrumentOpt = instrumentRepository.findBySymbol(cleanSymbol);

        if (instrumentOpt.isEmpty()) {
            log.warn("Instrument not found in DB for symbol: {} (cleaned: {})", yahooSymbol, cleanSymbol);
            return result;
        }

        Instrument instrument = instrumentOpt.get();

        for (int i = 0; i < timestamps.size(); i++) {
            try {
                if (opens.get(i).isNull() || highs.get(i).isNull() ||
                        lows.get(i).isNull() || closes.get(i).isNull()) {
                    continue; // Skip candles with null data (market holiday gaps)
                }

                long epochSecond = timestamps.get(i).asLong();
                OffsetDateTime timestamp = Instant.ofEpochSecond(epochSecond)
                        .atOffset(ZoneOffset.UTC);

                double open = opens.get(i).asDouble();
                double high = highs.get(i).asDouble();
                double low = lows.get(i).asDouble();
                double close = closes.get(i).asDouble();
                long volume = volumes.get(i).asLong(0);

                BigDecimal adjClose = null;
                if (adjCloses != null && i < adjCloses.size() && !adjCloses.get(i).isNull()) {
                    adjClose = BigDecimal.valueOf(adjCloses.get(i).asDouble());
                }

                // Skip if already exists
                if (marketDataRepository.existsByInstrumentIdAndTimestamp(instrument.getId(), timestamp)) {
                    continue;
                }

                MarketData marketData = MarketData.builder()
                        .instrument(instrument)
                        .timestamp(timestamp)
                        .open(BigDecimal.valueOf(open))
                        .high(BigDecimal.valueOf(high))
                        .low(BigDecimal.valueOf(low))
                        .close(BigDecimal.valueOf(close))
                        .volume(volume)
                        .adjClose(adjClose)
                        .build();

                result.add(marketData);

            } catch (Exception e) {
                log.warn("Error parsing OHLCV at index {} for {}: {}", i, yahooSymbol, e.getMessage());
            }
        }

        if (!result.isEmpty()) {
            List<MarketData> saved = marketDataRepository.saveAll(result);
            log.info("Saved {} new OHLCV records for {}", saved.size(), yahooSymbol);
            return saved;
        }

        return result;
    }

    private String cleanSymbolForDB(String yahooSymbol) {
        return switch (yahooSymbol) {
            case "^NSEI" -> "NIFTY50";
            case "^NSEBANK" -> "BANKNIFTY";
            case "GC=F" -> "GOLD";
            case "SI=F" -> "SILVER";
            case "CL=F" -> "CRUDEOIL";
            case "NG=F" -> "NATURALGAS";
            case "HG=F" -> "COPPER";
            default -> yahooSymbol.replace(".NS", "").replace(".BO", "");
        };
    }

    public List<String> getAllTrackedYahooSymbols() {
        List<String> all = new ArrayList<>(DEFAULT_NSE_SYMBOLS);
        all.addAll(DEFAULT_MCX_SYMBOLS);
        return all;
    }

    @Transactional(readOnly = true)
    public Optional<MarketData> getLatestQuote(String symbol) {
        return marketDataRepository.findLatestBySymbol(symbol);
    }
}
