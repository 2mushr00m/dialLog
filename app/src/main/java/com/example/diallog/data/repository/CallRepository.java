package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.data.model.CallRecord;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public interface CallRepository {
    List<CallRecord> getRecent(int offset, int limit);
    @Nullable CallRecord getByUri(Uri uri);

    default void setUserDirUri(@Nullable Uri uri) {}
    @NonNull
    default Future<?> refreshAsync() { return CompletableFuture.completedFuture(null); }

    default void ensureScanned() {}
    default void reload() {}

}
