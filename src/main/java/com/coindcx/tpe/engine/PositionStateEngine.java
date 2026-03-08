package com.coindcx.tpe.engine;

import com.coindcx.tpe.model.AccountInstrumentKey;
import com.coindcx.tpe.model.EventType;
import com.coindcx.tpe.model.PositionEvent;
import com.coindcx.tpe.model.PositionState;
import com.coindcx.tpe.model.PositionStatus;
import com.coindcx.tpe.publisher.AeronPublisher;
import com.coindcx.tpe.publisher.KafkaEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PositionStateEngine {
    private static final Logger logger = LoggerFactory.getLogger(PositionStateEngine.class);

    private final PositionStateStore store;
    private final AeronPublisher aeronPublisher;
    private final KafkaEventProducer kafkaProducer;

    public PositionStateEngine(PositionStateStore store, AeronPublisher aeronPublisher, 
                              KafkaEventProducer kafkaProducer) {
        this.store = store;
        this.aeronPublisher = aeronPublisher;
        this.kafkaProducer = kafkaProducer;
    }

    public void process(PositionEvent event) {
        logger.debug("Received event: [{}] {} qty={} leverage={}x side={}",
                event.getEventType(), event.getKey(), event.getQuantity(), 
                event.getLeverage(), event.getSide());

        AccountInstrumentKey key = event.getKey();
        PositionState newState;

        switch (event.getEventType()) {
            case OPEN:
                newState = new PositionState(key, event.getQuantity(), event.getLeverage(),
                        event.getSide(), PositionStatus.ACTIVE, event.getTimestamp());
                store.upsert(newState);
                logger.info("State updated: {} → {} qty={} leverage={}x",
                        key, newState.getStatus(), newState.getQuantity(), newState.getLeverage());
                break;

            case UPDATE:
                PositionState existing = store.get(key);
                if (existing == null) {
                    existing = new PositionState(key, 0.0, event.getLeverage(),
                            event.getSide(), PositionStatus.INACTIVE, event.getTimestamp());
                }
                
                PositionStatus newStatus = event.getQuantity() > 0 
                        ? PositionStatus.ACTIVE 
                        : PositionStatus.INACTIVE;
                
                newState = new PositionState(key, event.getQuantity(), event.getLeverage(),
                        event.getSide(), newStatus, event.getTimestamp());
                store.upsert(newState);
                
                if (newStatus == PositionStatus.INACTIVE) {
                    logger.info("Position CLOSED: {} → INACTIVE", key);
                } else {
                    logger.info("State updated: {} → {} qty={} leverage={}x",
                            key, newState.getStatus(), newState.getQuantity(), newState.getLeverage());
                }
                break;

            case CLOSE:
                newState = new PositionState(key, 0.0, 
                        event.getLeverage(), event.getSide(), 
                        PositionStatus.INACTIVE, event.getTimestamp());
                store.upsert(newState);
                logger.info("Position CLOSED: {} → INACTIVE", key);
                break;

            default:
                logger.warn("Unknown event type: {}", event.getEventType());
                return;
        }

        aeronPublisher.publishState(newState);
        kafkaProducer.publishState(newState);
    }
}
