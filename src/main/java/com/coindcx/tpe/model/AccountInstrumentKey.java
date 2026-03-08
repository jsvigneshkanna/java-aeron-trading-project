package com.coindcx.tpe.model;

import java.util.Objects;

public class AccountInstrumentKey {
    private final String accountId;
    private final String instrumentId;

    public AccountInstrumentKey(String accountId, String instrumentId) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountInstrumentKey that = (AccountInstrumentKey) o;
        return Objects.equals(accountId, that.accountId) && 
               Objects.equals(instrumentId, that.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, instrumentId);
    }

    @Override
    public String toString() {
        return accountId + ":" + instrumentId;
    }
}
