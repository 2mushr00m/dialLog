package com.example.diallog.data.repository;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.example.diallog.data.model.CallRecord;

import java.util.List;

public interface CallRepository {
    List<CallRecord> getRecent(int offset, int limit);
    @Nullable CallRecord getByUri(Uri uri);
    default void reload() {}

}
