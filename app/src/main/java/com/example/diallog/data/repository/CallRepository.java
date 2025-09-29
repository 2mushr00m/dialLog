package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.diallog.data.model.CallRecord;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public interface CallRepository {
    @Nullable CallRecord getByUri(Uri uri);
    List<CallRecord> getRecent(int offset, int pageSize);

    default void setTreeUri(@Nullable Uri uri) {}

    default void ensureScanned() {}
    default void reload() {}
    @NonNull
    default Future<?> refreshAsync() { return CompletableFuture.completedFuture(null); }


}
