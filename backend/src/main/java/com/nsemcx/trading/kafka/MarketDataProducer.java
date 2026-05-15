package com.nsemcx.trading.kafka;

import com.nsemcx.trading.config.KafkaConfig;
import com.nsemcx.trading.model.Alert;
import com.nsemcx.trading.model.MarketData;
import com.nsemcx.trading.model.OptionChain;
import com.nsemcx.trading.model.TradeSignal;
import com.nsemcx.trading.websocket.TradingWebSocketHandler.PaperTradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer that publishes domain events to the appropriate topics.
 *
 * <p>All send methods return a {@link CompletableFuture} so callers can either
 * await the result or simply fire-and-forget.  Delivery failures are logged at
 * ERROR level so they surface in alerting tools without crashing the caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Public send methods ───────────────────────────────────────────────────

    /**
     * Publishes an OHLCV candle to the {@code market-data} topic.
     * The partition key is the instrument id to guarantee ordering per instrument.
     */
    public CompletableFuture<SendResult<String, Object>> sendMarketData(MarketData data) {
        String key = String.valueOf(data.getInstrument().getId());
        return send(KafkaConfig.TOPIC_MARKET_DATA, key, data);
    }

    /**
     * Publishes a generated trade signal to the {@code trade-signals} topic.
     */
    public CompletableFuture<SendResult<String, Object>> sendTradeSignal(TradeSignal signal) {
        String key = signal.getInstrument().getSymbol();
        return send(KafkaConfig.TOPIC_TRADE_SIGNALS, key, signal);
    }

    /**
     * Publishes an alert event to the {@code alerts} topic.
     */
    public CompletableFuture<SendResult<String, Object>> sendAlert(Alert alert) {
        String key = alert.getInstrument() != null
                ? alert.getInstrument().getSymbol()
                : "system";
        return send(KafkaConfig.TOPIC_ALERTS, key, alert);
    }

    /**
     * Publishes an option-chain snapshot to the {@code option-chain} topic.
     */
    public CompletableFuture<SendResult<String, Object>> sendOptionChain(OptionChain chain) {
        String key = chain.getInstrument().getSymbol()
                + "-" + chain.getExpiry();
        return send(KafkaConfig.TOPIC_OPTION_CHAIN, key, chain);
    }

    /**
     * Publishes a paper-trade execution event to the {@code paper-trades} topic.
     */
    public CompletableFuture<SendResult<String, Object>> sendPaperTrade(PaperTradeEvent event) {
        String key = event.sessionId() + "-" + event.instrumentSymbol();
        return send(KafkaConfig.TOPIC_PAPER_TRADES, key, event);
    }

    // ── Private send helper ───────────────────────────────────────────────────

    private CompletableFuture<SendResult<String, Object>> send(
            String topic, String key, Object payload) {

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send message to topic={} key={}: {}",
                        topic, key, ex.getMessage(), ex);
            } else {
                log.debug("Sent to topic={} partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }
}
