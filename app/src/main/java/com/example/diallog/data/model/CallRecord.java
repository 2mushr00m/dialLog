package com.example.diallog.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class CallRecord {
    public final String path;
    public final String fileName;
    public final long durationMs;
    public final long startedAtEpochMs;

    public CallRecord(String path, String fileName, long durationMs, long startedAtEpochMs) {
        this.path = path;
        this.fileName = fileName;
        this.durationMs = durationMs;
        this.startedAtEpochMs = startedAtEpochMs;
    }

    @NonNull @Override
    public String toString() {
        return "CallRecord{" +
                "fileName='" + fileName + '\'' +
                ", durationMs=" + durationMs +
                ", startedAt=" + startedAtEpochMs +
                ", path=" + path +
                '}';
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CallRecord)) return false;
        CallRecord other = (CallRecord) obj;
        return path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}
