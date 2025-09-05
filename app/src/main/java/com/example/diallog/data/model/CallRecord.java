package com.example.diallog.data.model;

public final class CallRecord {
    public final String path;
    public final String fileName;
    public final long durationMs;
    public final long createdTime;

    public CallRecord(String path, String fileName, long durationMs, long createdTime) {
        this.path = path;
        this.fileName = fileName;
        this.durationMs = durationMs;
        this.createdTime = createdTime;
    }
}
