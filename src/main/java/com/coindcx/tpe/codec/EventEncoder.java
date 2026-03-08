package com.coindcx.tpe.codec;

import com.coindcx.tpe.model.PositionEvent;
import com.coindcx.tpe.model.PositionState;

import java.nio.charset.StandardCharsets;

public class EventEncoder {

    public byte[] encodeEvent(PositionEvent event) {
        String message = String.format("EVT|%s|%s|%s|%.8f|%.2f|%s|%d",
                event.getEventType().name(),
                event.getAccountId(),
                event.getInstrumentId(),
                event.getQuantity(),
                event.getLeverage(),
                event.getSide(),
                event.getTimestamp());
        return message.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] encodeState(PositionState state) {
        String message = String.format("STATE|%s|%s|%.8f|%.2f|%s|%s|%d",
                state.getKey().getAccountId(),
                state.getKey().getInstrumentId(),
                state.getQuantity(),
                state.getLeverage(),
                state.getSide(),
                state.getStatus().name(),
                state.getLastUpdatedAt());
        return message.getBytes(StandardCharsets.UTF_8);
    }
}
