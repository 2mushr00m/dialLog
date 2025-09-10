package com.example.diallog.data.repository;

import androidx.annotation.Nullable;

import com.example.diallog.data.model.CallRecord;

import java.util.List;

public interface CallRepository {
    List<CallRecord> getRecent(int offset, int limit);
    @Nullable CallRecord getByPath(String path);
    default void reload() {}

}
