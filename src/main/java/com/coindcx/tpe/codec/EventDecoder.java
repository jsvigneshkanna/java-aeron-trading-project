package com.coindcx.tpe.codec;

import com.coindcx.tpe.model.EventType;
import com.coindcx.tpe.model.PositionEvent;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class EventDecoder {
    private static final Logger logger = LoggerFactory.getLogger(EventDecoder.class);

    public Optional<PositionEvent> decode(DirectBuffer buffer, int offset, int length) {
        try {
            byte[] bytes = new byte[length];
            buffer.getBytes(offset, bytes);
            String message = new String(bytes, StandardCharsets.UTF_8);
            
            String[] parts = message.split("\\|");
            if (parts.length < 8 || !parts[0].equals("EVT")) {
                logger.warn("Malformed event message: {}", message);
                return Optional.empty();
            }

            EventType eventType = EventType.valueOf(parts[1]);
            String accountId = parts[2];
            String instrumentId = parts[3];
            double quantity = Double.parseDouble(parts[4]);
            double leverage = Double.parseDouble(parts[5]);
            String side = parts[6];
            long timestamp = Long.parseLong(parts[7]);

            return Optional.of(new PositionEvent(accountId, instrumentId, eventType,
                    quantity, leverage, side, timestamp));
        } catch (Exception e) {
            logger.error("Failed to decode event from buffer", e);
            return Optional.empty();
        }
    }
}
