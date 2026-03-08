package com.coindcx.tpe.ingestion;

import com.coindcx.tpe.config.KafkaConfig;
import com.coindcx.tpe.engine.PositionStateEngine;
import com.coindcx.tpe.model.EventType;
import com.coindcx.tpe.model.PositionEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaEventConsumer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private static final String TOPIC = System.getenv()
            .getOrDefault("KAFKA_POSITION_EVENTS_TOPIC", "position-events");
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(100);

    private final KafkaConsumer<String, String> consumer;
    private final PositionStateEngine engine;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running;

    public KafkaEventConsumer(PositionStateEngine engine) {
        this.consumer = new KafkaConsumer<>(KafkaConfig.getConsumerProperties());
        this.engine = engine;
        this.objectMapper = new ObjectMapper();
        this.running = new AtomicBoolean(false);
        this.consumer.subscribe(Collections.singletonList(TOPIC));
        logger.info("Kafka consumer subscribed to topic: {}", TOPIC);
    }

    @Override
    public void run() {
        running.set(true);
        logger.info("Kafka consumer started");

        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
                
                for (ConsumerRecord<String, String> record : records) {
                    processRecord(record);
                }
            } catch (Exception e) {
                logger.error("Error polling Kafka", e);
            }
        }

        logger.info("Kafka consumer stopped");
    }

    private void processRecord(ConsumerRecord<String, String> record) {
        try {
            JsonNode json = objectMapper.readTree(record.value());
            
            String accountId = json.get("accountId").asText();
            String instrumentId = json.get("instrumentId").asText();
            EventType eventType = EventType.valueOf(json.get("eventType").asText());
            double quantity = json.get("quantity").asDouble();
            double leverage = json.get("leverage").asDouble();
            String side = json.get("side").asText();
            long timestamp = json.get("timestamp").asLong();

            PositionEvent event = new PositionEvent(accountId, instrumentId, eventType,
                    quantity, leverage, side, timestamp);
            
            logger.debug("Received event from Kafka: {}", event);
            engine.process(event);
        } catch (Exception e) {
            logger.error("Failed to process Kafka record: {}", record.value(), e);
        }
    }

    public void stop() {
        running.set(false);
    }

    public void close() {
        stop();
        if (consumer != null) {
            consumer.close();
            logger.info("Kafka consumer closed");
        }
    }
}
