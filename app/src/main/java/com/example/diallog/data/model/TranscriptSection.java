package com.example.diallog.data.model;

import androidx.annotation.NonNull;

import java.util.List;

public class TranscriptSection {
    public final List<TranscriptSegment> items;

    public TranscriptSection(@NonNull List<TranscriptSegment> items) {
        this.items = items;
    }
}