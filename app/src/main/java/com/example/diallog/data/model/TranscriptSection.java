package com.example.diallog.data.model;

import androidx.annotation.NonNull;

import java.util.List;

public class TranscriptSection {
    public final String allText;
    public final int speakerId;

    public final List<Transcript> items;

    public TranscriptSection(String allText, int speakerId, @NonNull List<Transcript> items) {
        this.allText = allText;
        this.speakerId = speakerId;
        this.items = items;
    }
}