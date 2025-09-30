package com.example.diallog.data.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class CallRecord {
    public final @NonNull Uri uri;
    public final String fileName;
    public final long durationMs;
    public final long startedAtEpochMs;
    public boolean inCallHistory;
    public String summary;

    public CallRecord(@NonNull Uri uri, String fileName, long durationMs, long startedAtEpochMs) {
        this.uri = uri;
        this.fileName = fileName;
        this.durationMs = durationMs;
        this.startedAtEpochMs = startedAtEpochMs;
    }


    @Override
    public int hashCode() { return uri.hashCode(); }
}
