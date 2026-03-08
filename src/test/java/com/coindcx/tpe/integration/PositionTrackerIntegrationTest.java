package com.coindcx.tpe.integration;

import com.coindcx.tpe.engine.PositionStateEngine;
import com.coindcx.tpe.engine.PositionStateStore;
import com.coindcx.tpe.model.AccountInstrumentKey;
import com.coindcx.tpe.model.EventType;
import com.coindcx.tpe.model.PositionEvent;
import com.coindcx.tpe.model.PositionState;
import com.coindcx.tpe.model.PositionStatus;
import com.coindcx.tpe.publisher.AeronPublisher;
import com.coindcx.tpe.publisher.KafkaEventProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class PositionTrackerIntegrationTest {

    @Container
    private static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Mock
    private AeronPublisher mockAeronPublisher;

    @Mock
    private KafkaEventProducer mockKafkaProducer;

    private PositionStateStore store;
    private PositionStateEngine engine;
    private KafkaProducer<String, String> testProducer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        store = new PositionStateStore();

        engine = new PositionStateEngine(store, mockAeronPublisher, mockKafkaProducer);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        testProducer = new KafkaProducer<>(props);
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        if (testProducer != null) {
            testProducer.close();
        }
    }

    @Test
    void testThreeEventsFlow() throws Exception {
        String accountId = "TEST_ACC";
        String instrumentId = "BTCUSDT";
        AccountInstrumentKey key = new AccountInstrumentKey(accountId, instrumentId);

        PositionEvent event1 = new PositionEvent(accountId, instrumentId, EventType.OPEN,
                1.0, 10.0, "LONG", System.currentTimeMillis());
        
        PositionEvent event2 = new PositionEvent(accountId, instrumentId, EventType.UPDATE,
                2.5, 15.0, "LONG", System.currentTimeMillis() + 1000);
        
        PositionEvent event3 = new PositionEvent(accountId, instrumentId, EventType.UPDATE,
                3.0, 20.0, "LONG", System.currentTimeMillis() + 2000);

        engine.process(event1);
        engine.process(event2);
        engine.process(event3);

        PositionState finalState = store.get(key);
        assertNotNull(finalState);
        assertEquals(3.0, finalState.getQuantity());
        assertEquals(20.0, finalState.getLeverage());
        assertEquals("LONG", finalState.getSide());
        assertEquals(PositionStatus.ACTIVE, finalState.getStatus());
    }

    @Test
    void testOpenThenClose_setsInactive() throws Exception {
        String accountId = "TEST_ACC";
        String instrumentId = "ETHUSDT";
        AccountInstrumentKey key = new AccountInstrumentKey(accountId, instrumentId);

        PositionEvent openEvent = new PositionEvent(accountId, instrumentId, EventType.OPEN,
                5.0, 10.0, "SHORT", System.currentTimeMillis());
        engine.process(openEvent);

        PositionState stateAfterOpen = store.get(key);
        assertNotNull(stateAfterOpen);
        assertEquals(PositionStatus.ACTIVE, stateAfterOpen.getStatus());

        PositionEvent closeEvent = new PositionEvent(accountId, instrumentId, EventType.CLOSE,
                5.0, 10.0, "SHORT", System.currentTimeMillis() + 1000);
        engine.process(closeEvent);

        PositionState stateAfterClose = store.get(key);
        assertNotNull(stateAfterClose);
        assertEquals(0.0, stateAfterClose.getQuantity());
        assertEquals(PositionStatus.INACTIVE, stateAfterClose.getStatus());
    }

    @Test
    void testKafkaIntegration() throws Exception {
        String accountId = "KAFKA_TEST";
        String instrumentId = "SOLUSDT";
        String topic = "position-events";

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("accountId", accountId);
        eventMap.put("instrumentId", instrumentId);
        eventMap.put("eventType", "OPEN");
        eventMap.put("quantity", 10.0);
        eventMap.put("leverage", 5.0);
        eventMap.put("side", "LONG");
        eventMap.put("timestamp", System.currentTimeMillis());

        String json = objectMapper.writeValueAsString(eventMap);
        String key = accountId + ":" + instrumentId;

        testProducer.send(new ProducerRecord<>(topic, key, json)).get();

        PositionEvent event = new PositionEvent(accountId, instrumentId, EventType.OPEN,
                10.0, 5.0, "LONG", System.currentTimeMillis());
        engine.process(event);

        AccountInstrumentKey posKey = new AccountInstrumentKey(accountId, instrumentId);
        PositionState state = store.get(posKey);

        assertNotNull(state);
        assertEquals(10.0, state.getQuantity());
        assertEquals(5.0, state.getLeverage());
        assertEquals(PositionStatus.ACTIVE, state.getStatus());
    }
}
