package com.example.diallog.data.repository;

import com.example.diallog.data.model.CallRecord;

import java.util.List;

public interface CallRepository {
    List<CallRecord> getRecent(int offset, int limit);
    CallRecord getByPath(String path);

}
