package com.example.diallog.data.model;

import androidx.annotation.NonNull;

import java.util.List;

public final class CallRecordSection {
    public final String header;
    public final List<CallRecord> items;
    public CallRecordSection(@NonNull String header, @NonNull List<CallRecord> items) {
        this.header = header;
        this.items = items;
    }
}