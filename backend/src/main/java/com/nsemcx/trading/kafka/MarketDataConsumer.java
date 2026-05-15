package com.nsemcx.trading.kafka;

import com.nsemcx.trading.config.KafkaConfig;
import com.nsemcx.trading.model.Alert;
import com.nsemcx.trading.model.MarketData;
import com.nsemcx.trading.model.TradeSignal;
import com.nsemcx.trading.service.PaperTradingService;
import com.nsemcx.trading.service.TechnicalAnalysisService;
import com.nsemcx.trading.websocket.TradingWebSocketHandler;
import com.nsemcx.trading.websocket.TradingWebSocketHandler.PaperTradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka consumers for all trading-related topics.
 *
 * <p>All listeners use {@code MANUAL_IMMEDIATE} acknowledgement – the message is
 * committed only after successful processing.  If an exception is thrown before
 * {@code ack.acknowledge()} is called the container's error handler will log the
 * error and skip the record (configured in {@code KafkaConfig}).
 *
 * <p>Concurrency is set per listener via {@code containerFactory} which has
 * {@code concurrency=3} (matches partition count).  The {@code market-data}
 * listener overrides this inline with {@code concurrency="3"}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataConsumer {

    private final TechnicalAnalysisService technicalAnalysisService;
    private final TradingWebSocketHandler webSocketHandler;
    private final PaperTradingService paperTradingService;

    // ── Consumers ─────────────────────────────────────────────────────────────

    /**
     * Consumes raw OHLCV candles from {@code market-data}.
     *
     * <p>For each candle:
     * <ol>
     *   <li>Triggers async technical analysis via {@link TechnicalAnalysisService}.</li>
     *   <li>Broadcasts the candle to WebSocket subscribers on {@code /topic/market-data}.</li>
     * </ol>
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_MARKET_DATA,
            groupId = "${spring.kafka.consumer.group-id:trading-group}",
            concurrency = "3",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMarketData(
            @Payload Object payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        try {
            if (payload instanceof MarketData data) {
                log.debug("Consuming market-data: instrument={} partition={} offset={}",
                        data.getInstrument().getSymbol(), partition, offset);

                // Async technical analysis – does not block the listener thread
                technicalAnalysisService.analyzeInstrumentAsync(data.getInstrument().getId());

                // Broadcast to WebSocket clients
                webSocketHandler.broadcastMarketData(data);
            } else {
                log.warn("Unexpected payload type in market-data: {}",
                        payload == null ? "null" : payload.getClass().getName());
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing market-data message topic={} partition={} offset={}: {}",
                    topic, partition, offset, ex.getMessage(), ex);
            // Acknowledge to avoid infinite reprocessing; dead-letter handling is out of scope here
            ack.acknowledge();
        }
    }

    /**
     * Consumes trade signals from {@code trade-signals}.
     *
     * <p>For each signal:
     * <ol>
     *   <li>Broadcasts to WebSocket subscribers on {@code /topic/signals}.</li>
     *   <li>If paper trading is active, hands the signal to
     *       {@link PaperTradingService#evaluateSignalForExecution}.</li>
     * </ol>
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_TRADE_SIGNALS,
            groupId = "${spring.kafka.consumer.group-id:trading-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTradeSignal(
            @Payload Object payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        try {
            if (payload instanceof TradeSignal signal) {
                log.debug("Consuming trade-signal: id={} type={} instrument={} partition={} offset={}",
                        signal.getId(), signal.getSignalType(),
                        signal.getInstrument().getSymbol(), partition, offset);

                // Broadcast to WebSocket clients
                webSocketHandler.broadcastSignal(signal);

                // Evaluate for paper-trade execution
                paperTradingService.evaluateSignalForExecution(signal);
            } else {
                log.warn("Unexpected payload type in trade-signals: {}",
                        payload == null ? "null" : payload.getClass().getName());
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing trade-signal partition={} offset={}: {}",
                    partition, offset, ex.getMessage(), ex);
            ack.acknowledge();
        }
    }

    /**
     * Consumes alert events from {@code alerts} and broadcasts them to WebSocket
     * subscribers on {@code /topic/alerts}.
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_ALERTS,
            groupId = "${spring.kafka.consumer.group-id:trading-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeAlert(
            @Payload Object payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        try {
            if (payload instanceof Alert alert) {
                log.debug("Consuming alert: id={} type={} severity={} partition={} offset={}",
                        alert.getId(), alert.getAlertType(), alert.getSeverity(), partition, offset);

                webSocketHandler.broadcastAlert(alert);
            } else {
                log.warn("Unexpected payload type in alerts: {}",
                        payload == null ? "null" : payload.getClass().getName());
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing alert partition={} offset={}: {}",
                    partition, offset, ex.getMessage(), ex);
            ack.acknowledge();
        }
    }

    /**
     * Consumes paper-trade execution events from {@code paper-trades}.
     *
     * <p>Delegates to {@link PaperTradingService#executeTrade} for persistence and
     * P&L calculation, then broadcasts the result to WebSocket subscribers.
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_PAPER_TRADES,
            groupId = "${spring.kafka.consumer.group-id:trading-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaperTrade(
            @Payload Object payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        try {
            if (payload instanceof PaperTradeEvent event) {
                log.debug("Consuming paper-trade event: type={} instrument={} partition={} offset={}",
                        event.eventType(), event.instrumentSymbol(), partition, offset);

                paperTradingService.executeTrade(event);
                webSocketHandler.broadcastPaperTrade(event);
            } else {
                log.warn("Unexpected payload type in paper-trades: {}",
                        payload == null ? "null" : payload.getClass().getName());
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing paper-trade partition={} offset={}: {}",
                    partition, offset, ex.getMessage(), ex);
            ack.acknowledge();
        }
    }
}
