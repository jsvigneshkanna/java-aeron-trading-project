package com.coindcx.tpe.publisher;

import com.coindcx.tpe.config.KafkaConfig;
import com.coindcx.tpe.model.PositionEvent;
import com.coindcx.tpe.model.PositionState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class KafkaEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(KafkaEventProducer.class);

    private static final String POSITION_EVENTS_TOPIC = System.getenv()
            .getOrDefault("KAFKA_POSITION_EVENTS_TOPIC", "position-events");
    private static final String STATE_UPDATES_TOPIC = System.getenv()
            .getOrDefault("KAFKA_STATE_UPDATES_TOPIC", "position-state-updates");

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public KafkaEventProducer() {
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.objectMapper = new ObjectMapper();
        logger.info("Kafka producer initialized");
    }

    protected KafkaEventProducer(KafkaProducer<String, String> producer) {
        this.producer = producer;
        this.objectMapper = new ObjectMapper();
    }

    public void publishEvent(PositionEvent event) {
        if (producer == null) {
            return;
        }
        try {
            Map<String, Object> eventMap = new HashMap<>();
            eventMap.put("accountId", event.getAccountId());
            eventMap.put("instrumentId", event.getInstrumentId());
            eventMap.put("eventType", event.getEventType().name());
            eventMap.put("quantity", event.getQuantity());
            eventMap.put("leverage", event.getLeverage());
            eventMap.put("side", event.getSide());
            eventMap.put("timestamp", event.getTimestamp());

            String json = objectMapper.writeValueAsString(eventMap);
            String key = event.getAccountId() + ":" + event.getInstrumentId();

            ProducerRecord<String, String> record = new ProducerRecord<>(POSITION_EVENTS_TOPIC, key, json);
            
            producer.send(record, (RecordMetadata metadata, Exception exception) -> {
                if (exception != null) {
                    logger.error("Failed to publish event to Kafka", exception);
                } else {
                    logger.debug("📨 Published to Kafka topic={} partition={} offset={}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event to JSON", e);
        }
    }

    public void publishState(PositionState state) {
        if (producer == null) {
            return;
        }
        try {
            Map<String, Object> stateMap = new HashMap<>();
            stateMap.put("accountId", state.getKey().getAccountId());
            stateMap.put("instrumentId", state.getKey().getInstrumentId());
            stateMap.put("quantity", state.getQuantity());
            stateMap.put("leverage", state.getLeverage());
            stateMap.put("side", state.getSide());
            stateMap.put("status", state.getStatus().name());
            stateMap.put("lastUpdatedAt", state.getLastUpdatedAt());

            String json = objectMapper.writeValueAsString(stateMap);
            String key = state.getKey().getAccountId() + ":" + state.getKey().getInstrumentId();

            ProducerRecord<String, String> record = new ProducerRecord<>(STATE_UPDATES_TOPIC, key, json);
            
            producer.send(record, (RecordMetadata metadata, Exception exception) -> {
                if (exception != null) {
                    logger.error("Failed to publish state to Kafka", exception);
                } else {
                    logger.debug("📨 Published to Kafka topic={} partition={} offset={}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize state to JSON", e);
        }
    }

    public void close() {
        if (producer != null) {
            producer.flush();
            producer.close();
            logger.info("Kafka producer closed");
        }
    }
}
