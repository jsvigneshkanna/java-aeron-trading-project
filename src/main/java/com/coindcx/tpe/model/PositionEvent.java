package com.coindcx.tpe.model;

public class PositionEvent {
    private final String accountId;
    private final String instrumentId;
    private final EventType eventType;
    private final double quantity;
    private final double leverage;
    private final String side;
    private final long timestamp;

    public PositionEvent(String accountId, String instrumentId, EventType eventType,
                        double quantity, double leverage, String side, long timestamp) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.eventType = eventType;
        this.quantity = quantity;
        this.leverage = leverage;
        this.side = side;
        this.timestamp = timestamp;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getLeverage() {
        return leverage;
    }

    public String getSide() {
        return side;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public AccountInstrumentKey getKey() {
        return new AccountInstrumentKey(accountId, instrumentId);
    }

    @Override
    public String toString() {
        return String.format("PositionEvent{%s, type=%s, qty=%.4f, leverage=%.1fx, side=%s, ts=%d}",
                getKey(), eventType, quantity, leverage, side, timestamp);
    }
}
