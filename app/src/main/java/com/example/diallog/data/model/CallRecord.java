package com.example.diallog.data.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class CallRecord {
    public final @NonNull Uri uri;
    public final String fileName;
    public final long durationMs;
    public final long startedAtEpochMs;

    public CallRecord(@NonNull Uri uri, String fileName, long durationMs, long startedAtEpochMs) {
        this.uri = uri;
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
                ", uri=" + uri +
                '}';
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CallRecord)) return false;
        CallRecord other = (CallRecord) obj;
        return uri.equals(other.uri);
    }

    @Override
    public int hashCode() { return uri.hashCode(); }
}
