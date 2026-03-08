package com.coindcx.tpe.engine;

import com.coindcx.tpe.model.AccountInstrumentKey;
import com.coindcx.tpe.model.PositionState;
import com.coindcx.tpe.model.PositionStatus;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PositionStateStore {
    private final ConcurrentHashMap<AccountInstrumentKey, PositionState> store;

    public PositionStateStore() {
        this.store = new ConcurrentHashMap<>();
    }

    public void upsert(PositionState state) {
        store.put(state.getKey(), state);
    }

    public PositionState get(AccountInstrumentKey key) {
        return store.get(key);
    }

    public Collection<PositionState> getAll() {
        return store.values();
    }

    public Collection<PositionState> getActive() {
        return store.values().stream()
                .filter(state -> state.getStatus() == PositionStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    public Collection<PositionState> getInactive() {
        return store.values().stream()
                .filter(state -> state.getStatus() == PositionStatus.INACTIVE)
                .collect(Collectors.toList());
    }

    public int size() {
        return store.size();
    }
}
