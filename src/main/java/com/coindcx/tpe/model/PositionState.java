package com.coindcx.tpe.model;

public class PositionState {
    private final AccountInstrumentKey key;
    private final double quantity;
    private final double leverage;
    private final String side;
    private final PositionStatus status;
    private final long lastUpdatedAt;

    public PositionState(AccountInstrumentKey key, double quantity, double leverage,
                        String side, PositionStatus status, long lastUpdatedAt) {
        this.key = key;
        this.quantity = quantity;
        this.leverage = leverage;
        this.side = side;
        this.status = status;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public AccountInstrumentKey getKey() {
        return key;
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

    public PositionStatus getStatus() {
        return status;
    }

    public long getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    @Override
    public String toString() {
        return String.format("PositionState{%s, qty=%.4f, leverage=%.1fx, side=%s, status=%s, updated=%d}",
                key, quantity, leverage, side, status, lastUpdatedAt);
    }
}
