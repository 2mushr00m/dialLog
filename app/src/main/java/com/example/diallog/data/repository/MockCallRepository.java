package com.example.diallog.data.repository;

import androidx.annotation.NonNull;

import com.example.diallog.data.model.CallRecord;

import java.util.ArrayList;
import java.util.List;

public final class MockCallRepository implements CallRepository {
    private final List<CallRecord> fake = new ArrayList<>();

    public MockCallRepository() {
        fake.add(new CallRecord("/tmp/a.mp3", "통화1", 120000, System.currentTimeMillis()));
        fake.add(new CallRecord("/tmp/b.mp3", "통화2", 60000, System.currentTimeMillis() - 3600_000));
    }

    @Override
    public List<CallRecord> getRecent(int offset, int limit) {
        int end = Math.min(offset + limit, fake.size());
        if (offset >= fake.size()) return new ArrayList<>();
        return new ArrayList<>(fake.subList(offset, end));
    }

    @Override
    public CallRecord getByPath(@NonNull String path) {
        for (CallRecord r : fake) if (r.path.equals(path)) return r;
        return null;
    }
}