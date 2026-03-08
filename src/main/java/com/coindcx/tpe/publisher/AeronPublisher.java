package com.coindcx.tpe.publisher;

import com.coindcx.tpe.codec.EventEncoder;
import com.coindcx.tpe.model.PositionEvent;
import com.coindcx.tpe.model.PositionState;
import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AeronPublisher {
    private static final Logger logger = LoggerFactory.getLogger(AeronPublisher.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_SLEEP_MS = 1;

    private final Publication publication;
    private final EventEncoder encoder;
    private final UnsafeBuffer buffer;

    public AeronPublisher(Aeron aeron, String channel, int streamId) {
        if (aeron != null) {
            this.publication = aeron.addPublication(channel, streamId);
            logger.info("Aeron publication created on channel={} streamId={}", channel, streamId);
        } else {
            this.publication = null;
        }
        this.encoder = new EventEncoder();
        this.buffer = new UnsafeBuffer(new byte[4096]);
    }

    public void publishEvent(PositionEvent event) {
        if (publication == null) {
            return;
        }
        byte[] encoded = encoder.encodeEvent(event);
        publish(encoded);
    }

    public void publishState(PositionState state) {
        if (publication == null) {
            return;
        }
        byte[] encoded = encoder.encodeState(state);
        publish(encoded);
        logger.debug("📡 Published to Aeron streamId={}", publication.streamId());
    }

    private void publish(byte[] data) {
        buffer.putBytes(0, data);
        
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            long result = publication.offer(buffer, 0, data.length);
            
            if (result > 0) {
                return;
            } else if (result == Publication.BACK_PRESSURED || result == Publication.ADMIN_ACTION) {
                logger.debug("Aeron back-pressure, retry {}/{}", retry + 1, MAX_RETRIES);
                try {
                    Thread.sleep(RETRY_SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted during retry sleep", e);
                    return;
                }
            } else if (result == Publication.NOT_CONNECTED) {
                logger.warn("Aeron publication NOT_CONNECTED");
                return;
            } else if (result == Publication.CLOSED) {
                logger.error("Aeron publication CLOSED");
                return;
            }
        }
        
        logger.warn("Failed to publish to Aeron after {} retries", MAX_RETRIES);
    }

    public void close() {
        if (publication != null) {
            publication.close();
            logger.info("Aeron publication closed");
        }
    }
}
