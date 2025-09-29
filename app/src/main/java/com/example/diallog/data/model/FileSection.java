package com.example.diallog.data.model;

import androidx.annotation.NonNull;

import java.util.List;

public final class FileSection {
    public final String header;
    public final List<CallRecord> items;
    public FileSection(@NonNull String header, @NonNull List<CallRecord> items) {
        this.header = header;
        this.items = items;
    }
}