package com.nsemcx.trading.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka infrastructure configuration.
 *
 * Topics (all 3 partitions, replication-factor 1 for single-broker dev):
 *   market-data        – OHLCV candle events from Yahoo Finance
 *   trade-signals      – generated buy/sell/hold signals
 *   alerts             – price / technical alert events
 *   option-chain       – NSE option chain snapshots
 *   backtest-results   – completed back-test run summaries
 *   paper-trades       – paper-trading order events
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConfig {

    // ------------------------------------------------------------------ topic names

    public static final String TOPIC_MARKET_DATA     = "market-data";
    public static final String TOPIC_TRADE_SIGNALS   = "trade-signals";
    public static final String TOPIC_ALERTS          = "alerts";
    public static final String TOPIC_OPTION_CHAIN    = "option-chain";
    public static final String TOPIC_BACKTEST_RESULTS= "backtest-results";
    public static final String TOPIC_PAPER_TRADES    = "paper-trades";

    private static final int PARTITIONS   = 3;
    private static final int REPLICATION  = 1;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:trading-group}")
    private String groupId;

    // ------------------------------------------------------------------ KafkaAdmin

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(config);
    }

    // ------------------------------------------------------------------ topic beans

    @Bean
    public org.apache.kafka.clients.admin.NewTopic marketDataTopic() {
        return TopicBuilder.name(TOPIC_MARKET_DATA)
                .partitions(PARTITIONS)
                .replicas(REPLICATION)
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic tradeSignalsTopic() {
        return TopicBuilder.name(TOPIC_TRADE_SIGNALS)
                .partitions(PARTITIONS)
                .replicas(REPLICATION)
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic alertsTopic() {
        return TopicBuilder.name(TOPIC_ALERTS)
                .partitions(PARTITIONS)
                .replicas(REPLICATION)
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic optionChainTopic() {
        return TopicBuilder.name(TOPIC_OPTION_CHAIN)
                .partitions(PARTITIONS)
                .replicas(REPLICATION)
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic backtestResultsTopic() {
        return TopicBuilder.name(TOPIC_BACKTEST_RESULTS)
                .partitions(PARTITIONS)
                .replicas(REPLICATION)
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic paperTradesTopic() {
        return TopicBuilder.name(TOPIC_PAPER_TRADES)
                .partitions(PARTITIONS)
                .replicas(REPLICATION)
                .build();
    }

    // ------------------------------------------------------------------ producer

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        // Add type mapping header so consumers can deserialise cleanly
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true);
        return template;
    }

    // ------------------------------------------------------------------ consumer

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.nsemcx.trading.*");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        // Fallback: if no type header present, deserialise as generic Map
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Container factory used by all @KafkaListener methods.
     * Ack mode: MANUAL_IMMEDIATE – consumers must call Acknowledgment.acknowledge() explicitly.
     * Concurrency: 3 threads per listener by default (matches partition count).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(PARTITIONS);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setObservationEnabled(true);
        // Log and continue on deserialization errors rather than crashing the listener
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
                (rec, ex) -> log.error("Kafka message error – topic={} partition={} offset={}: {}",
                        rec.topic(), rec.partition(), rec.offset(), ex.getMessage())
        ));
        return factory;
    }
}
