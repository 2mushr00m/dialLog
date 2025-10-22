package com.example.diallog.data.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class CallRecord {
    public final @NonNull Uri uri;
    public String fileName;
    public long durationMs;
    public long startedAtEpochMs;
    public String summary;
    public boolean hasCache;
    public boolean inCallHistory;

    public CallRecord(@NonNull Uri uri) {
        this.uri = uri;
    }


    @Override
    public int hashCode() { return uri.hashCode(); }
}
