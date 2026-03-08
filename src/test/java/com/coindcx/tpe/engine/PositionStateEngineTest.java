package com.coindcx.tpe.engine;

import com.coindcx.tpe.model.AccountInstrumentKey;
import com.coindcx.tpe.model.EventType;
import com.coindcx.tpe.model.PositionEvent;
import com.coindcx.tpe.model.PositionState;
import com.coindcx.tpe.model.PositionStatus;
import com.coindcx.tpe.publisher.AeronPublisher;
import com.coindcx.tpe.publisher.KafkaEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PositionStateEngineTest {

    @Mock
    private AeronPublisher aeronPublisher;

    @Mock
    private KafkaEventProducer kafkaProducer;

    private PositionStateStore store;
    private PositionStateEngine engine;

    @BeforeEach
    void setUp() {
        store = new PositionStateStore();
        engine = new PositionStateEngine(store, aeronPublisher, kafkaProducer);
    }

    @Test
    void testOpenPosition_createsActiveState() {
        PositionEvent event = new PositionEvent(
                "ACC1", "BTCUSDT", EventType.OPEN,
                1.5, 10.0, "LONG", System.currentTimeMillis());

        engine.process(event);

        AccountInstrumentKey key = new AccountInstrumentKey("ACC1", "BTCUSDT");
        PositionState state = store.get(key);

        assertNotNull(state);
        assertEquals(1.5, state.getQuantity());
        assertEquals(10.0, state.getLeverage());
        assertEquals("LONG", state.getSide());
        assertEquals(PositionStatus.ACTIVE, state.getStatus());

        verify(aeronPublisher).publishState(any(PositionState.class));
        verify(kafkaProducer).publishState(any(PositionState.class));
    }

    @Test
    void testUpdatePosition_updatesLeverageAndQty() {
        PositionEvent openEvent = new PositionEvent(
                "ACC1", "ETHUSDT", EventType.OPEN,
                2.0, 5.0, "SHORT", System.currentTimeMillis());
        engine.process(openEvent);

        PositionEvent updateEvent = new PositionEvent(
                "ACC1", "ETHUSDT", EventType.UPDATE,
                3.5, 8.0, "SHORT", System.currentTimeMillis() + 1000);
        engine.process(updateEvent);

        AccountInstrumentKey key = new AccountInstrumentKey("ACC1", "ETHUSDT");
        PositionState state = store.get(key);

        assertNotNull(state);
        assertEquals(3.5, state.getQuantity());
        assertEquals(8.0, state.getLeverage());
        assertEquals("SHORT", state.getSide());
        assertEquals(PositionStatus.ACTIVE, state.getStatus());
    }

    @Test
    void testUpdatePosition_zeroQty_setsInactive() {
        PositionEvent openEvent = new PositionEvent(
                "ACC1", "BTCUSDT", EventType.OPEN,
                1.0, 10.0, "LONG", System.currentTimeMillis());
        engine.process(openEvent);

        PositionEvent updateEvent = new PositionEvent(
                "ACC1", "BTCUSDT", EventType.UPDATE,
                0.0, 10.0, "LONG", System.currentTimeMillis() + 1000);
        engine.process(updateEvent);

        AccountInstrumentKey key = new AccountInstrumentKey("ACC1", "BTCUSDT");
        PositionState state = store.get(key);

        assertNotNull(state);
        assertEquals(0.0, state.getQuantity());
        assertEquals(PositionStatus.INACTIVE, state.getStatus());
    }

    @Test
    void testClosePosition_setsInactiveZeroQty() {
        PositionEvent openEvent = new PositionEvent(
                "ACC1", "SOLUSDT", EventType.OPEN,
                10.0, 3.0, "LONG", System.currentTimeMillis());
        engine.process(openEvent);

        PositionEvent closeEvent = new PositionEvent(
                "ACC1", "SOLUSDT", EventType.CLOSE,
                10.0, 3.0, "LONG", System.currentTimeMillis() + 2000);
        engine.process(closeEvent);

        AccountInstrumentKey key = new AccountInstrumentKey("ACC1", "SOLUSDT");
        PositionState state = store.get(key);

        assertNotNull(state);
        assertEquals(0.0, state.getQuantity());
        assertEquals(PositionStatus.INACTIVE, state.getStatus());
    }

    @Test
    void testMultipleAccounts_trackedIndependently() {
        PositionEvent event1 = new PositionEvent(
                "ACC1", "BTCUSDT", EventType.OPEN,
                1.0, 10.0, "LONG", System.currentTimeMillis());
        
        PositionEvent event2 = new PositionEvent(
                "ACC2", "BTCUSDT", EventType.OPEN,
                2.0, 5.0, "SHORT", System.currentTimeMillis());

        engine.process(event1);
        engine.process(event2);

        AccountInstrumentKey key1 = new AccountInstrumentKey("ACC1", "BTCUSDT");
        AccountInstrumentKey key2 = new AccountInstrumentKey("ACC2", "BTCUSDT");

        PositionState state1 = store.get(key1);
        PositionState state2 = store.get(key2);

        assertNotNull(state1);
        assertNotNull(state2);
        assertEquals(1.0, state1.getQuantity());
        assertEquals(2.0, state2.getQuantity());
        assertEquals("LONG", state1.getSide());
        assertEquals("SHORT", state2.getSide());
    }

    @Test
    void testSameAccount_differentInstruments_tracked_separately() {
        PositionEvent event1 = new PositionEvent(
                "ACC1", "BTCUSDT", EventType.OPEN,
                1.0, 10.0, "LONG", System.currentTimeMillis());
        
        PositionEvent event2 = new PositionEvent(
                "ACC1", "ETHUSDT", EventType.OPEN,
                5.0, 20.0, "SHORT", System.currentTimeMillis());

        engine.process(event1);
        engine.process(event2);

        AccountInstrumentKey key1 = new AccountInstrumentKey("ACC1", "BTCUSDT");
        AccountInstrumentKey key2 = new AccountInstrumentKey("ACC1", "ETHUSDT");

        PositionState state1 = store.get(key1);
        PositionState state2 = store.get(key2);

        assertNotNull(state1);
        assertNotNull(state2);
        assertEquals("BTCUSDT", state1.getKey().getInstrumentId());
        assertEquals("ETHUSDT", state2.getKey().getInstrumentId());
        assertEquals(1.0, state1.getQuantity());
        assertEquals(5.0, state2.getQuantity());
    }
}
